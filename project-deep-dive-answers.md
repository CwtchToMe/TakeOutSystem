# TakeoutSystem 项目深度拷打 — 面试问题集答案

> 本文件对 project-deep-dive.md 中的196个问题逐一作答，基于项目实际代码实现进行分析。

---

## 一、整体架构与设计决策

### 1. 项目从微服务架构改造为单体，这个过程中面临的最大技术挑战是什么？原来的微服务拆分粒度是怎么划分的（每个服务管几个表）？

**回答：**

从代码结构推断，原微服务架构大致拆分为以下服务：
- **用户服务**：t_user、t_user_address（2张表）
- **商家服务**：t_merchant、t_category（2张表）
- **商品服务**：t_dish、t_dish_spec（2张表）
- **订单服务**：t_order、t_order_item、t_cart（3张表）
- **优惠券服务**：t_coupon、t_user_coupon（2张表）
- **评价服务**：t_review（1张表）
- **收藏服务**：t_favorite（1张表）

改造为单体面临的最大技术挑战：
1. **分布式事务降级为本地事务**：原本需要 Seata/TCC 的跨服务事务，现在可以用 `@Transactional`，但需要确保所有操作在同一个事务管理器下
2. **服务间通信方式变更**：从 Feign/REST 调用变为直接的 Java 方法调用，原来的容错机制（降级、熔断、超时）全部失效
3. **数据一致性策略调整**：原本通过最终一致性保证的业务（如订单完成后更新销量、评分），现在可以在同一事务中同步完成，但耦合度增加
4. **共享资源竞争**：原来各服务独立的连接池、Redis连接池现在合并，需要重新评估容量
5. **代码组织结构调整**：需要合理划分包结构，避免模块间循环依赖

### 2. 单体改造后，原本的跨服务调用现在变成了同进程调用，但代码中仍然保留了 OrderService 依赖 MerchantService、DishService、CouponService 这样的横向调用链——这种互相依赖是否符合"聚合优于组合"的设计原则？如果未来需要拆分回微服务，这种侵入式的 Service 层直接引用会带来什么改造困难？

**回答：**

**是否符合"聚合优于组合"原则：**
- 不符合。当前 OrderService 直接依赖了5个其他 Service（DishService、CouponService、MerchantService、UserAddressService、CartService），形成了"神级 Service"模式
- "聚合优于组合"强调通过聚合根来封装业务逻辑，而不是直接依赖外部服务的内部实现
- 正确的做法应该是通过领域事件或防腐层来解耦

**拆回微服务的改造困难：**
1. **接口契约缺失**：当前是 Java 方法签名调用，没有明确的 API 契约（DTO、错误码、超时约定），拆分成 RPC 时需要重新定义所有接口
2. **事务边界模糊**：目前用 `@Transactional` 包裹了跨模块操作，拆分后需要引入分布式事务（TCC/Saga/可靠消息）
3. **循环依赖难解**：如果 A 依赖 B，B 又依赖 A，微服务化时会出现循环调用死锁
4. **数据一致性保障难度大**：原本本地事务保证的一致性，拆分后需要靠最终一致性补偿
5. **测试复杂度激增**：单体时可以一次启动测试所有模块，拆分后需要 mock 大量外部服务

### 3. 项目使用 @RequiredArgsConstructor + final 字段做构造器注入，为什么不用 @Autowired 字段注入？构造器注入在循环依赖场景下会出现什么问题？当前模块之间是否存在隐式的循环依赖？

**回答：**

**为什么用构造器注入而不是 @Autowired 字段注入：**
1. **不可变性**：final 字段保证依赖注入后不可修改，防止意外覆盖
2. **依赖完整性**：构造器强制要求所有必需依赖在对象创建时就提供，避免 NPE
3. **便于测试**：单元测试时可以直接 new 传入 mock 对象，不需要反射或 Spring 测试上下文
4. **符合 Spring 推荐**：Spring 4.x 起官方推荐构造器注入，字段注入被认为是 anti-pattern
5. **循环依赖早期发现**：构造器注入在启动时就会抛出 BeanCurrentlyInCreationException，而字段注入会在运行时才暴露问题

**构造器注入在循环依赖场景下的问题：**
- 两个 Bean 互相通过构造器注入对方时，Spring 容器启动时会直接抛出 `BeanCurrentlyInCreationException`
- 字段注入/setter 注入可以通过三级缓存提前暴露代理对象解决循环依赖，但构造器注入不行（因为对象还没创建完）

**当前是否存在隐式循环依赖：**
从代码分析，存在以下潜在循环依赖路径：
- OrderService → DishService → MerchantService → Merchant（自身没问题，单向）
- OrderService → CouponService（无反向依赖）
- ReviewService → OrderService → ...（ReviewService 依赖 OrderService，但 OrderService 不依赖 ReviewService）
- 目前没有发现 A→B→A 的直接循环依赖，但 OrderService 的"大管家"模式是循环依赖的高风险区

### 4. 所有 Service 层都直接抛出 BusinessException，由 GlobalExceptionHandler 统一捕获——为什么不在各 Service 内自行处理异常并返回 Result<?> 类型？这种设计对单元测试的 mock 友好度如何？如果 Service 层方法签名不体现任何错误可能性，调用者如何知道哪些操作可能失败？

**回答：**

**为什么用异常而不是返回 Result<?>：**
1. **关注点分离**：Service 层专注业务逻辑，异常处理交给全局拦截器，代码更简洁
2. **事务回滚**：Spring 的 `@Transactional` 默认对 RuntimeException 回滚，抛出 BusinessException（继承 RuntimeException）能正确触发回滚
3. **调用链简洁**：不需要每层都检查 Result.isSuccess() 再决定是否继续，异常自动向上传播
4. **错误语义明确**：不同的异常类型（BusinessException、ParamErrorException）天然区分错误类别

**对单元测试 mock 友好度：**
- 友好度中等。mock 时需要设置 when(...).thenThrow(new BusinessException(...)) 来模拟异常场景
- 相比返回 Result<?>，异常方式的测试代码更接近真实运行时行为
- 缺点是测试时需要显式 try-catch 或用 assertThrows，断言稍繁琐

**调用者如何知道哪些操作可能失败：**
- 从方法签名上看不出来（Java 的 RuntimeException 不需要在 throws 中声明）
- 只能通过：1) 阅读源码/注释 2) 阅读 API 文档 3) 业务常识判断
- 这是 unchecked exception 模式的固有缺陷，解决方案是：在 Javadoc 中注明可能抛出的异常，或使用自定义异常类命名体现业务含义

### 5. Entity 类使用 @Data，当实体之间存在关联关系时，@EqualsAndHashCode 会包含所有字段，包括 id，这在集合操作中会带来什么问题？@ToString 在双向关联时是否会导致 StackOverflow？

**回答：**

**@EqualsAndHashCode 包含所有字段（含 id）的问题：**
1. **集合操作语义错误**：如果对象存入 Set 后修改了非 id 字段，equals/hashCode 会变化，导致从 Set 中找不到该对象（"丢失"元素）
2. **业务相等 vs 数据库相等混淆**：业务上两个对象如果所有业务字段相同但 id 不同（比如两条相同的订单明细），按 @Data 的逻辑会被认为不相等，但业务上可能认为是重复的
3. **JPA 实体状态问题**：new 状态（id=null）和持久化状态（id=xxx）的同一个对象，hashCode 不同，不能正确放入 HashSet/HashMap
4. **正确做法**：Entity 类应该只使用 id 字段生成 equals/hashCode，或用 @EqualsAndHashCode(of = "id") 显式指定

**@ToString 在双向关联时的 StackOverflow 风险：**
- 当前项目中实体类之间没有显式的双向关联（如 Order 里没有 List<OrderItem> 字段，OrderItem 里也没有 Order 字段），都是通过 Mapper 单独查询，所以不存在 StackOverflow 问题
- 但如果未来加了双向关联（比如 @OneToMany + @ManyToOne），同时两边都有 @Data 默认的 toString，就会导致递归调用 toString 引发 StackOverflowError
- 防御方式：使用 @ToString.Exclude 排除关联字段，或手动重写 toString

### 6. 整个项目只有 2 个 MyBatis XML 映射文件，其余 7 个 Mapper 全部继承 BaseMapper<T> 零 SQL。这种做法的前提条件是什么？在什么场景下必须回退到 XML/注解写 SQL？当前这 2 个 XML 里面的 SQL 有没有你不能容忍的写法？

**回答：**

**零 SQL 的前提条件：**
1. **单表操作为主**：绝大多数 CRUD 都是单表操作，不需要 JOIN
2. **实体与表严格映射**：字段名通过下划线转驼峰能正确对应
3. **查询条件简单**：通过 QueryWrapper/UpdateWrapper 能表达的条件（等值、范围、排序、模糊查询等）
4. **对性能要求不极端**：MyBatis Plus 生成的 SQL 是通用型的，极端场景下可能不是最优

**必须回退到 XML/注解的场景：**
1. **多表 JOIN 查询**：如菜单查询需要关联分类表和菜品表
2. **复杂聚合查询**：如 AVG、SUM、GROUP BY 等
3. **特殊数据库函数**：如 ST_Distance_Sphere 地理距离计算
4. **动态 SQL 过于复杂**：用 QueryWrapper 写起来可读性差
5. **批量操作需要优化**：如批量 INSERT、批量 UPSERT
6. **子查询、EXISTS、UNION 等复杂 SQL**

**关于当前 2 个 XML 的 SQL 质量：**
- DishMapper.xml 的菜单查询用 LEFT JOIN，是合理的
- MerchantMapper.xml 的附近商家查询用 ST_Distance_Sphere，功能上正确但性能堪忧（全表计算距离）
- 潜在问题：没有用空间索引，数据量大时会成为性能瓶颈
- 可改进点：附近商家查询应该先用经纬度范围粗筛（MBRContains）再精确计算距离

---

## 二、认证模块（Auth）

### 7. JwtUtil 被设计为工具类（final class + 私有构造器 + static 方法），为什么不用 Spring 管理的 Bean？parseToken 返回 null 而不是抛出异常的设计意图是什么？调用者每次都要做 null 检查，这算不算违背 Fail-Fast 原则？

**回答：**

**为什么用静态工具类而不是 Spring Bean：**
1. **无状态性**：JWT 操作不依赖任何 Spring 管理的状态，纯函数式操作
2. **使用便捷**：不需要注入，任何地方都能直接调用
3. **历史习惯**：JWT 工具类通常作为通用工具，不绑定 Spring 容器
4. **缺点**：无法利用 Spring 的配置注入（secret 通过参数传入而非 @Value），不方便做 AOP 监控

**parseToken 返回 null 而非抛异常的设计意图：**
- 设计上认为"Token 无效/过期"是**正常业务场景**而非异常，所以用返回 null 表示解析失败
- 避免在拦截器等高频调用处频繁 try-catch，减少异常堆栈创建开销
- 调用方可以根据 null 直接返回 401，逻辑更直观

**算不算违背 Fail-Fast 原则：**
- 从"快速暴露问题"的角度看，有一点违背。如果调用方忘记 null 检查，后续代码会 NPE，错误堆栈指向 NPE 而不是 JWT 解析失败，增加排查难度
- 但从"预期内的失败用返回值，预期外的失败用异常"的原则看，Token 失效是认证场景下的预期失败，用 null 是合理的
- 折中方案：提供两个方法，parseTokenOrNull() 和 parseTokenOrThrow()，让调用方按需选择

### 8. JwtUtil.generateToken() 的 claims() 方法（JJWT 0.12.x）和旧版本 setClaims() 的行为差异是什么？项目里 claims(Map.of(...)) 和 .subject() 同时调用，最终 Token 的 sub 声明和自定义 userId 声明之间是什么关系？客户端解析时应以哪个为准？

**回答：**

**JJWT 0.12.x claims() 与旧版 setClaims() 的差异：**
- **旧版 setClaims(Map)**：会**覆盖**所有已设置的 claims（包括 subject、issuedAt 等标准声明），调用顺序不同结果不同
- **新版 claims(Map)**：是**合并**语义，将 map 中的 claim 添加到已有 claims 中，已有的标准声明（如 subject）不会被覆盖
- 0.12.x 的 builder 是不可变模式，每次调用返回新的 builder 实例

**sub 声明与 userId 声明的关系：**
- 代码中同时设置了 `.subject(String.valueOf(userId))` 和 `.claims(Map.of("userId", userId, ...))`
- 最终 Token 中同时存在 `sub`（标准声明，值为 userId 的字符串）和 `userId`（自定义声明，值为 Long 类型）
- 两者值相同但类型不同：sub 是 String，userId 是 Number
- 这是冗余设计：`sub` 是 JWT 标准的"主体"声明，`userId` 是自定义声明

**客户端解析应以哪个为准：**
- 标准做法应使用 `sub`（subject）声明，因为这是 JWT 规范定义的标准字段
- 但本项目的代码中实际使用的是自定义的 `userId` 声明（getUserId 方法从 claims.get("userId") 取值）
- 风险：如果未来只改了 subject 而忘了改 userId（或反之），会出现不一致
- 建议：统一使用标准的 `sub` 声明，减少冗余

### 9. JWT secret 硬编码在 application.yml 中，要求至少 32 字节。如果未来密钥轮换（secret rotation），怎么实现新旧 Token 同时生效的过渡期？现有的架构改动量多大？

**回答：**

**密钥轮换的过渡期方案：**
1. **多密钥验证**：解析 Token 时尝试用新密钥解析，如果失败再用旧密钥尝试，都失败才返回无效
2. **Token 中携带密钥标识**：在 JWT 的 header 中加入 `kid`（Key ID）声明，解析时根据 kid 选择对应的密钥验证
3. **新旧 Token 刷新机制**：用户用旧 Token 请求成功后，响应头中返回新 Token（用新密钥签发），前端自动替换

**现有架构的改动量：**
- **JwtUtil**：需要改造 parseToken 支持多密钥列表遍历解析，或根据 kid 选密钥
- **JwtProperties**：从单一 secret 改为 List<SecretConfig>（含 id、secret、生效时间等）
- **AuthInterceptor / AuthService**：基本不需要改，因为它们调用的是 JwtUtil 的高层接口
- **Redis 白名单/黑名单**：如果依赖 Token 内容做校验，不需要改
- 估算：核心改动在 JwtUtil 和配置类，约 100-200 行代码，属于中小改动量

**当前硬编码的风险：**
- secret 直接写在 yml 里，如果代码仓库公开，等于把密钥给了攻击者
- 正确做法：通过环境变量 `${JWT_SECRET:}` 或配置中心注入，默认值为空启动报错

### 10. AuthInterceptor 的 whitelist 路径匹配使用 AntPathMatcher，但商家详情接口 /api/merchant/{id} 却单独用正则匹配——为什么不统一用 AntPathMatcher 的 /** 通配？这种不一致后续扩展新公开接口时会带来怎样的维护成本？

**回答：**

**为什么不统一用 AntPathMatcher：**
- 代码注释没有说明原因，推测是开发者不知道 `AntPathMatcher` 支持 `{id}` 变量匹配（即 `/api/merchant/{id}` 可以直接写在 whitelist 里，AntPathMatcher 能正确匹配）
- 或者担心 `/api/merchant/nearby`、`/api/merchant/search` 也被 `{id}` 匹配到？但 AntPathMatcher 的优先级是精确匹配优于变量匹配，nearby 和 search 是精确路径，不会被数字 id 模式误匹配
- 实际上 AntPathMatcher 完全支持 `/api/merchant/{id}` 或 `/api/merchant/*` 的写法

**维护成本：**
1. **新人理解成本高**：看到两种不同的匹配方式，会疑惑为什么不统一，需要花时间确认行为是否一致
2. **新增白名单时容易出错**：如果新增的接口是路径变量模式，开发者可能不知道该加在哪、用哪种写法
3. **修改成本**：如果未来要换匹配策略（如加日志、加审计），需要改两处逻辑
4. **bug 风险**：两种路径可能存在重叠或遗漏，比如 `/api/merchant/abc` 不会被 AntPathMatcher 匹配（不在 whitelist），也不会被 `\d+` 正则匹配（不是数字），结果是正确的（需要鉴权），但逻辑分散容易遗漏边界情况

**修复方式：**
- 把 `/api/merchant/{id}` 加入 WHITELIST 列表，用 AntPathMatcher 统一管理
- 或写成 `/api/merchant/*`（但要注意 nearby/search 已经在列表里了，AntPathMatcher 会优先匹配更长的精确路径）

### 11. UserContext 使用 ThreadLocal 存储登录用户信息，afterCompletion 中调用 clear()。如果某个 Handler 在处理过程中新开了一个子线程（@Async、线程池、CompletableFuture），子线程里还能拿到用户信息吗？这会造成什么问题？有什么解决方案？

**回答：**

**子线程能否拿到用户信息：**
- 不能。ThreadLocal 是线程隔离的，子线程不会继承父线程的 ThreadLocal 值
- 除非使用 InheritableThreadLocal，但 Spring 默认的 ThreadLocal 不是 Inheritable 的

**会造成的问题：**
1. **子线程中 UserContext.getUser() 返回 null**，导致"未登录"异常或 NPE
2. **权限校验失效**：如果异步任务中有权限检查，会因为拿不到用户角色而拒绝或降级
3. **数据错乱**：如果异步任务用了用户信息但拿到 null，可能触发默认值逻辑（如 UserRole.CUSTOMER），导致越权或数据错误
4. **MDC 日志追踪断裂**：如果用 MDC 存 traceId，子线程也会丢失

**解决方案：**
1. **TransmittableThreadLocal (TTL)**：阿里开源的 TTL 库，配合线程池修饰，能传递 ThreadLocal 到子线程
2. **手动传递**：在提交异步任务前，把用户上下文取出作为参数传入子线程
3. **@Async 配置定制**：自定义 ThreadPoolTaskExecutor，在任务包装时复制 UserContext
4. **使用 InheritableThreadLocal**：但线程池场景下有问题（线程复用时不会重新设置），只适合 new Thread() 场景
5. **Spring Security 方式**：如果用 Spring Security，它的 SecurityContextHolder 已经处理了这个问题（有 MODE_INHERITABLETHREADLOCAL 模式）

### 12. accessToken 有效期 2 小时，refreshToken 7 天。如果用户在 accessToken 过期前 1 分钟发送请求，请求到达后端时 Token 刚好过期，网关/拦截器返回 401——前端应如何处理这种边界情况？当前项目的前端实现有没有处理 Token 自动续期？

**回答：**

**前端处理边界情况的方案：**
1. **主动续期（推荐）**：前端在每次请求前检查 accessToken 的过期时间，如果剩余时间小于阈值（如 5 分钟），先调用 refresh 接口换新 token 再发请求
2. **被动续期（401 重试）**：收到 401 后，判断如果是 token 过期，则自动调用 refresh 接口换新 token，然后重试原请求（最多重试 1 次，防止死循环）
3. **双 Token 并行刷新**：refreshToken 也快要过期时，刷新 accessToken 的同时返回新的 refreshToken（滑动过期）

**当前前端实现：**
需要检查前端代码，但从后端代码看：
- 后端提供了 `/api/auth/refresh` 接口用于刷新 accessToken
- refreshToken 存在 Redis 中，可以校验有效性
- 但后端没有实现"滑动刷新"（每次 refresh 不更新 refreshToken 本身的过期时间），7 天后用户必须重新登录

**边界情况的用户体验：**
- 如果前端没有自动续期，用户操作到一半弹出登录页，体验很差
- 最佳实践是：axios 拦截器中统一处理 401，自动刷新 token 并重试，刷新失败才跳转登录

### 13. AuthService.login() 中，验证码是 "123456" 时直接 if 跳过 Redis 校验。如果生产环境忘了改这段代码会有什么后果？这段逻辑是否应该抽到配置中，通过 @ConditionalOnProperty 控制？

**回答：**

**生产环境忘记修改的后果：**
1. **任意手机号登录**：攻击者只要知道任意一个手机号（甚至可以猜），输入验证码 123456 就能登录该账号
2. **管理员账号被盗**：如果知道管理员手机号，可以直接登录管理员后台，后果严重
3. **用户数据泄露**：登录后可以查看用户的所有订单、地址、优惠券等隐私信息
4. **资金损失风险**：如果有支付相关操作，可能导致用户财产损失

**是否应该用 @ConditionalOnProperty 控制：**
- **应该**，而且是必须的。这是典型的"开发便利功能"，绝对不能泄漏到生产环境
- 推荐实现方式：
  ```java
  @ConditionalOnProperty(name = "app.sms.master-code-enabled", havingValue = "true", matchIfMissing = false)
  ```
- 或者更简单：用 `@Profile("dev")` 配合不同的 SmsService 实现，DevSmsServiceImpl 里有万能码，ProdSmsServiceImpl 里没有
- 默认值必须是 false，即生产环境默认不启用万能验证码

**当前代码的问题：**
- 没有任何开关控制，全靠开发者"记得改"，这是非常危险的
- 正确的安全原则是：默认安全，开发便利功能需要显式开启

### 14. AuthService.logout() 只删除了 Redis 中的 refreshToken，accessToken 因为是无状态 JWT 无法主动失效。如果用户声称账号被盗，管理员需要让该用户立刻下线，现有机制能做到吗？不能的话，该怎么加？

**回答：**

**现有机制能做到吗：**
- 不能完全做到。logout 只删了 refreshToken，accessToken 在过期前仍然有效（最多 2 小时）
- 如果攻击者已经拿到了 accessToken，在 2 小时内仍然可以操作

**解决方案：**

1. **Token 黑名单机制（推荐）**：
   - 在 Redis 中维护一个 token 黑名单（或叫"已失效 token 集合"）
   - 用户主动登出或管理员强制下线时，把当前 accessToken 加入黑名单，设置过期时间等于 token 剩余有效期
   - 拦截器解析 token 后，检查该 token 是否在黑名单中，如果在则拒绝
   - 优点：精准失效，不影响其他设备登录
   - 缺点：每次请求多一次 Redis 查询

2. **用户版本号机制（更省空间）**：
   - 在 JWT 的 claims 中加入 tokenVersion 字段
   - Redis 中存储每个用户的当前 tokenVersion
   - 强制下线时，Redis 中的 tokenVersion +1
   - 拦截器校验：JWT 中的 tokenVersion 必须等于 Redis 中的版本号
   - 优点：每个用户只存一个数字，空间占用极小
   - 缺点：会让该用户所有设备都下线（全端踢下线）

3. **缩短 accessToken 有效期**：
   - 把 2 小时改成 15 分钟，降低泄露后的风险窗口
   - 配合 refreshToken 实现无感续期

推荐方案：用户版本号机制 + 缩短 accessToken 有效期，兼顾安全和性能。

---

## 三、用户模块（User）

### 15. UserService.getOrCreate() 在用户首次登录时自动创建 CUSTOMER 角色账号。如果恶意攻击者持续用不同的手机号调用登录接口，会导致数据库用户表无限膨胀。应该怎么防御？

**回答：**

**攻击场景：**
- 攻击者用随机手机号批量调用 `/api/auth/sendSmsCode` + `/api/auth/login`
- 因为登录即注册，每个新手机号都会在数据库插入一条用户记录
- 最终导致用户表数据膨胀，影响查询性能和存储空间

**防御措施：**

1. **IP 限流**：
   - 对登录/注册接口按 IP 限流（如每分钟最多 5 次）
   - 可以用 Redis + 拦截器实现，或引入 Sentinel

2. **验证码防刷**：
   - 当前的验证码是固定 123456，形同虚设
   - 生产环境接入真实短信服务后，短信验证码本身就能防刷（获取验证码需要真的发短信，有成本）
   - 加上图形验证码或滑块验证码，防止自动化脚本

3. **手机号格式校验**：
   - 严格校验手机号格式（正则匹配国内手机号段），减少无效号码
   - 但攻击者可以用接码平台的真实手机号，所以只是第一道防线

4. **设备指纹/风控**：
   - 基于设备 ID、IP、行为模式做风控检测
   - 异常设备直接拒绝注册

5. **注册与登录分离（可选）**：
   - 改成先注册再登录，注册需要更多信息（昵称、密码等），增加攻击成本
   - 但外卖场景通常希望一键登录，所以短信验证码 + 限流更合适

6. **清理僵尸账号**：
   - 定时任务清理注册后从未下单的僵尸账号
   - 或者把这些账号标记为"待激活"，首次下单时才转正

### 16. 昵称生成逻辑是 "用户" + phone.substring(phone.length() - 4)，如果手机号是国际格式（如 +8613800000001），取后 4 位会取到什么？会不会生成重复昵称？

**回答：**

**国际格式手机号的截取结果：**
- 例如 `+8613800000001`，长度是 14 位
- `phone.substring(10)` → `0001`
- 昵称为 "用户0001"

**但实际上有更大的问题：**
- 当前数据库表 t_user 的 phone 字段是 `VARCHAR(20)`，能存下国际号码
- 但如果手机号带 `+` 或 `-`，substring 截取的结果可能不是预期的后 4 位数字
- 更严重的是：**没有 +86 前缀的国内手机号和带 +86 的同一手机号会被认为是两个不同用户**（因为唯一索引 uk_phone 是精确匹配）

**昵称重复问题：**
- 会重复。因为后 4 位只有 0000-9999 共一万种组合，用户量超过 1 万后必然出现重复昵称
- 而且同一个尾号的不同手机号（如 13800000001 和 13900000001）昵称都是"用户0001"
- 但昵称重复不影响业务（昵称不是唯一索引），只是用户体验差点

**改进建议：**
1. 手机号统一格式化（存储 E.164 格式，如 +8613800000000）
2. 昵称生成加上随机数或自增后缀，降低重复概率
3. 或者允许用户首次登录后设置昵称

### 17. UserService.updateStatus() 的 status 参数允许 0 或 1，但 UserAdminController 的 @Valid 校验只能校验基本类型——如果传 2 或 3，会走到 Service 层才抛出异常。Controller 层能否提前拦截这种无效参数？为什么没做？

**回答：**

**Controller 层能否提前拦截：**
- 能。有多种方式：
  1. **自定义校验注解**：`@IntValue(values = {0, 1}, message = "状态只能为0或1")`
  2. **枚举类型**：把 status 改成枚举，前端传枚举名（如 "DISABLED"、"NORMAL"）
  3. **正则校验**：`@Pattern(regexp = "^[01]$")`（如果是 String 类型）
  4. **在 Controller 方法里手动判断**，不优雅但简单

**为什么没做：**
1. **开发效率优先**：当前是单体项目快速迭代，把校验集中在 Service 层，Controller 只做转发，减少重复代码
2. **参数类型混用**：有些接口用 Integer 接收 status，@Valid 对 Integer 只能做 @NotNull @Min @Max，无法限定具体枚举值
3. **项目规模小**：初期项目小，Service 层校验已经够用，没有花精力做分层校验
4. **没有统一的参数校验规范**：团队没有约定哪些校验在 Controller 层、哪些在 Service 层

**最佳实践：**
- Controller 层做**格式校验**（非空、长度、格式、取值范围）
- Service 层做**业务校验**（状态流转合法性、权限、数据一致性）
- 状态值校验属于"格式校验"，应该在 Controller 层拦截，减少无效请求打到 Service 层

### 18. 收货地址模块使用 LambdaUpdateWrapper 的 set(UserAddress::getIsDefault, 0) 清空旧默认地址，如果 clearDefault() 和后续的 setDefault() 之间发生并发，会出现多个默认地址吗？@Transactional 能保证在这个场景下的隔离性吗？

**回答：**

**并发下是否会出现多个默认地址：**
- 取决于这两个操作是否在同一个事务中，以及事务的隔离级别
- 如果 clearDefault() 和 setDefault() 是同一个事务内的两次 UPDATE，且用 `@Transactional` 包裹：
  - **RC 隔离级别**（MySQL 默认的 READ COMMITTED）：可能出现。因为两个并发事务都能读到对方提交前的状态，都执行 set 0 清空，然后都 set 1 设置自己为默认，最终两个地址都是默认
  - **RR 隔离级别**（REPEATABLE READ，MySQL InnoDB 默认）：也可能出现。MySQL 的 RR 是快照读，但 UPDATE 是当前读，两个事务的 UPDATE 都能成功（因为更新的是不同行），最后都提交，导致两个默认地址
- **结论：@Transactional 不能防止多个默认地址的问题**

**解决方案：**
1. **乐观锁/版本号**：不太适用，因为更新的是多行
2. **分布式锁**：按 userId 加锁，同一用户的地址修改串行化
3. **数据库唯一索引**：增加 `UNIQUE KEY uk_user_default (user_id, is_default)` 但有问题——非默认地址 is_default=0 会重复，MySQL 唯一索引不允许。变通：用 NULL 表示非默认，因为唯一索引允许多个 NULL
4. **单条 SQL 原子操作**：用一条 SQL 完成"清除旧默认 + 设置新默认"——但做不到，因为需要更新不同的行
5. **SELECT ... FOR UPDATE**：在事务内先 `SELECT * FROM t_user_address WHERE user_id = ? FOR UPDATE` 锁定该用户的所有地址行，然后再修改，利用行锁 + Gap Lock 防止并发插入
6. **应用层分布式锁**：最简单，对每个 userId 的地址操作加 Redis 锁

### 19. UserAddressService.add() 中的 clearDefault() 为什么不在 DAO 层用 UPDATE ... SET is_default = 0 WHERE user_id = ? 一条 SQL 完成？拆成两步操作在并发下的安全性如何？

**回答：**

**为什么不用一条 SQL：**
- 推测是 MyBatis Plus 的使用习惯问题。LambdaUpdateWrapper 也能写这个条件，一句 `update(null, wrapper.set(..., 0).eq(...))` 就能搞定
- 或者是"先查后改"的思维定式：先查哪些是默认地址，再修改
- 但从代码上看，如果 clearDefault() 本身就是一条 UPDATE（不是先查再逐条更新），那一条 SQL 和两条 SQL 在功能上没区别

**拆成两步（clearDefault + setDefault）的并发安全性：**
- 即使两条 SQL 都在同一个事务里，也**不能保证并发安全**（如上一题分析）
- 但如果是一条 SQL 的话，会不会更安全？其实也不会，因为问题的本质是"两个事务都要 set 1 为默认"，和几条 SQL 无关
- 一条 SQL 的唯一好处是减少了网络往返，性能更好

**正确的并发安全方案：**
- 利用数据库唯一约束 + 应用层容错：设置 `UNIQUE KEY uk_user_default(user_id, is_default)`，非默认地址用 NULL（因为唯一索引允许多个 NULL），默认地址用 1。设置默认地址时如果报唯一键冲突，说明已有默认地址，这是不可能的（因为我们之前已经清除了），但至少不会出现多个默认
- 更简单的方案：Redis 分布式锁 `lock:address:default:{userId}`

---

## 四、商家模块（Merchant）

### 20. Merchant 表用 status 字段同时表示五个状态，这种"单字段多含义"的设计在业务扩展时会有什么问题？如果将来要新增"暂停营业"和"装修中"状态，现有代码需要改多少处？

**回答：**

**单字段多状态的问题：**
1. **状态含义耦合**：审核状态（审核中、审核拒绝、封禁）和营业状态（营业中、打烊）混在一个字段里，概念上是两个维度
2. **状态流转复杂**：审核中→营业中是审核维度，营业中↔打烊是营业维度，但是用同一个字段控制，流转判断容易出错
3. **扩展困难**：如果要增加"暂停营业"（商家主动暂停，不同于打烊），需要修改所有涉及 status 判断的地方
4. **权限判断分散**：不同角色能修改的状态范围不同，散落在各 Service 方法中

**新增"暂停营业"和"装修中"需要改的地方（估算）：**
1. MerchantService.register() — 注册状态初始化（可能不改）
2. MerchantService.updateStatus() — 营业状态切换（需要加判断，哪些状态之间可以互转）
3. MerchantService.audit() — 审核逻辑（可能不改）
4. MerchantService.nearby() — 附近商家只查营业中（需要过滤新增状态？）
5. MerchantService.search() — 搜索只查营业中（同上）
6. OrderService.submit() — 下单校验商家状态（需要判断哪些状态允许下单）
7. MerchantController / MerchantAdminController — 状态参数校验
8. 前端商家列表、订单列表等页面的状态显示
9. 数据库 comment 注释

保守估计：**后端 8-12 处，前端 5-8 处**，而且很容易漏改。

**改进建议：**
- 拆分成两个字段：`audit_status`（审核状态：0审核中 1通过 2拒绝 3封禁）和 `business_status`（营业状态：0休息 1营业中 2打烊 3暂停 4装修中）
- 或者用枚举类统一管理所有状态流转逻辑，避免散落各处

### 21. MerchantService.nearby() 使用 MySQL 的 ST_Distance_Sphere 计算球面距离，一万家商家同时查询时这个查询的性能如何？为什么不用 Redis Geo？如果在查询高峰期，这个接口会成为瓶颈吗？

**回答：**

**MySQL ST_Distance_Sphere 的性能：**
- 一万家商家的场景下，每次查询都要对所有符合条件的商家计算距离，是 O(N) 的全表扫描级操作
- 即使有 `idx_location (longitude, latitude)` 复合索引，如果不用范围查询先缩小范围，索引也用不上
- 1万数据量下，单次查询可能在几十毫秒级，但如果 QPS 高（如每秒上百次），数据库 CPU 会被距离计算打满
- 数据量到十万级时，这个接口一定会慢

**为什么不用 Redis Geo：**
- 可能是项目初期数据量小，MySQL 够用，没考虑优化
- 或者开发者不熟悉 Redis Geo 命令
- Redis Geo 的优势：
  - 距离计算在 Redis 内存中完成，速度快
  - 支持按半径范围查询（GEORADIUS），天然适合附近商家
  - 减轻数据库压力
- Redis Geo 的劣势：
  - 数据一致性问题：商家新增/修改时需要同步更新 Redis
  - 多条件筛选麻烦：Redis Geo 只能按距离查，不能同时按评分、销量等筛选

**高峰期会成为瓶颈吗：**
- **会**。附近商家是首页接口，访问频率最高
- 一万商家 + 高 QPS 情况下，MySQL 的 CPU 会成为瓶颈
- 优化方向：
  1. 先用经纬度矩形范围粗筛（MBRContains），减少参与距离计算的行数
  2. 加空间索引（SPATIAL INDEX），MySQL 支持空间索引加速空间查询
  3. 引入 Redis Geo 承担附近查询
  4. 加缓存：同一经纬度附近的结果可以短时间缓存

### 22. 附近商家接口使用了 LIMIT #{offset}, #{size} 做分页，在大偏移量场景下（如翻到第 100 页）会有什么性能问题？为什么不用游标分页或"加载更多"的分页模式？

**回答：**

**大偏移量分页的性能问题：**
- `LIMIT 1000, 10` 需要扫描前 1000 条数据后丢弃，偏移量越大越慢
- 第 100 页（每页 10 条）就是 `LIMIT 990, 10`，MySQL 要排序后跳过 990 条
- 如果有索引能利用到排序字段，性能会好些，但 OFFSET 大了仍然慢
- 附近商家查询更特殊：需要先计算距离再排序，无法利用索引避免排序，大偏移量更慢

**为什么不用游标分页/加载更多：**
- 项目初期，用户通常不会翻到第 100 页附近商家，翻几页找不到满意的就换搜索词了
- 传统分页（跳转到指定页）实现简单，前端组件也成熟
- 游标分页需要前端记录上次的游标（lastId 或 lastDistance），实现稍复杂

**适合用游标分页的场景：**
- 附近商家列表（下拉加载更多，不需要跳页）
- 订单列表（用户通常按时间顺序翻，不需要跳页）
- 评价列表

**游标分页的优势：**
- 性能稳定，不管翻多少页，查询效率都是 O(logN)
- 数据实时性更好，不会因为中间新增/删除数据导致重复或遗漏

**建议：**
- 附近商家接口改成"加载更多"模式，用距离作为游标（记录最后一条的距离和 id，下一页查距离 >= lastDistance 且 id > lastId）
- 管理后台的分页保留传统分页方式（运营需要跳页）

### 23. MerchantService.getDetail() 使用了 Redis 缓存，但缓存对象是 MerchantVO——这个 VO 包含了完整的经纬度、营业时间、评分等所有信息。如果商家只修改了店名，缓存会被整体淘汰还是局部更新？这种"全量缓存"策略在频繁更新场景下的命中率如何？

**回答：**

**缓存更新策略：**
- 代码中 `updateMy()` 和 `updateStatus()`、`audit()`、`updateScore()` 等方法都是调用 `redisUtil.delete(CACHE_KEY + merchantId)` 来**整体淘汰**缓存
- 不是局部更新，是删了等下次查询时重新从数据库加载

**全量缓存 + 淘汰策略的优缺点：**
- **优点**：实现简单，不会出现局部更新遗漏字段导致的数据不一致
- **缺点**：每次修改都会失效缓存，下次查询需要回源 DB

**频繁更新场景下的命中率：**
- 如果商家频繁修改信息（比如改店名、改营业状态、评分因评价而频繁更新），缓存命中率会很低
- 特别是评分更新：每次有新评价就调用 `updateScore()` 淘汰缓存，热门商家评价多，缓存几乎永远失效
- 但商家信息修改通常不频繁，评分更新也只是热门商家有问题，所以大多数场景下命中率应该还可以

**改进建议：**
1. **缓存时间缩短**：从 30 分钟改成 5 分钟，降低不一致的时间窗口
2. **局部更新**：对于简单字段更新（如 status、score），直接更新缓存中的对应字段，而不是删除整个缓存
3. **读写分离策略**：读多写少的用缓存，写频繁的字段（如 score）直接查数据库，或异步更新缓存
4. **评分异步聚合**：评分不要每次评价都实时计算并更新缓存，改成定时任务每 5 分钟聚合一次，减少缓存失效频率

### 24. Merchant 实体中 openTime 和 closeTime 是 LocalTime 类型，但没有在 Service 中做营业时间校验——如果商家设置的 closeTime < openTime（如早 9 点到晚 2 点，实际是跨天），下单接口不会拦截。应该在哪儿做这个校验？

**回答：**

**校验应该放在哪一层：**
1. **商家设置营业时间时（MerchantService.updateMy / register）**：这是第一道防线，应该校验 closeTime 和 openTime 的合法性，以及是否跨天
2. **用户下单时（OrderService.submit）**：第二道防线，校验当前时间是否在商家营业范围内

**跨天营业时间的处理：**
- 跨天场景是真实存在的（如夜宵店 21:00 - 03:00）
- 判断逻辑：
  ```java
  LocalTime now = LocalTime.now();
  if (openTime.isBefore(closeTime)) {
      // 正常：9:00 - 22:00
      return !now.isBefore(openTime) && !now.isAfter(closeTime);
  } else {
      // 跨天：21:00 - 03:00
      return !now.isBefore(openTime) || !now.isAfter(closeTime);
  }
  ```

**为什么没做：**
- 可能是业务初期只考虑了正常营业时间，没考虑跨天场景
- 或者是觉得商家自己不会设置错
- 但实际上这是一个必做的校验，否则：
  - 商家设置了跨天营业时间，下单时判断错误，用户在营业时间内却下不了单
  - 或者商家设置了错误的时间（close < open），导致用户永远下不了单

**建议：**
1. 在商家注册和修改信息时增加营业时间合法性校验
2. 在下单接口增加"当前是否营业"的校验
3. 考虑增加"营业日期"字段（如工作日/周末/节假日不同营业时间），但复杂度更高

### 25. 商家注册后状态为 0（审核中），但此时的商家已经通过 getDetail 接口暴露给所有用户（该接口是白名单公开接口），这合理吗？审核中的商家信息是否需要对外隐藏？

**回答：**

**不合理，审核中的商家应该对外隐藏。**

**原因：**
1. **信息质量控制**：审核中的商家信息可能不完整或不符合规范，不应该展示给用户
2. **防止滥注册**：如果注册就能被搜索到，攻击者可以批量注册垃圾商家
3. **用户体验**：用户搜到审核中的商家，进去发现不能下单（因为 submit 校验了 status=1），会困惑
4. **合规要求**：外卖平台通常要求商家证照齐全才能上线展示

**当前代码的实际情况：**
- `getDetail()` 直接查 ID 返回，没有过滤 status
- `nearby()` 和 `search()` 都有 `.eq(Merchant::getStatus, 1)`，只查营业中的商家
- 也就是说，审核中的商家**不能通过搜索/附近列表发现**，但**如果有人知道 ID 直接访问详情页是可以看到的**
- 这属于"半暴露"状态，风险相对较低但仍然不合规

**修复方式：**
- 在 `getDetail()` 中增加状态过滤：审核中(0)、封禁(3)、审核拒绝(4)的商家不对外展示
- 或者更细粒度：审核中(0)的商家展示"审核中"提示，不展示菜单和下单按钮
- 封禁(3)和审核拒绝(4)的商家直接返回 404 或"商家不存在"

---

## 五、商品模块（Category + Dish + Menu）

### 26. CategoryService.delete() 中先 SELECT COUNT(*) 检查分类下有没有菜品，再执行 DELETE——这两个操作不在同一个事务中，如果在检查和删除之间另一个线程插入了菜品到该分类，删除会成功吗？数据会变脏吗？

**回答：**

**并发场景分析：**
- 线程 A：检查分类下菜品数 = 0，准备删除分类
- 线程 B：在该分类下插入一个菜品
- 线程 A：删除分类成功
- 结果：分类被删了，但有菜品的 category_id 指向一个不存在的分类

**数据是否变脏：**
- **会变脏**。出现"孤儿菜品"——category_id 指向已删除的分类
- 但因为有逻辑删除（deleted 字段），如果菜品查询时 JOIN 分类表，可能查不到分类信息，显示异常
- 如果分类是物理删除，问题更严重，category_id 变成无效外键

**为什么没加事务：**
- 从代码看，可能开发者忘了加 `@Transactional`
- 或者觉得分类删除是低频操作，并发概率低

**修复方案：**
1. **加 @Transactional**：把检查和删除放在同一个事务里——但这还不够！因为 RR 隔离级别下，SELECT COUNT 是快照读，看不到其他事务未提交的插入，仍然可能有问题
2. **SELECT ... FOR UPDATE**：在事务内用 `SELECT COUNT(*) ... FOR UPDATE` 锁定相关行（但这是 COUNT，锁什么行呢？）
3. **外键约束**：数据库层面加外键，删除分类时如果有菜品引用，数据库直接报错
4. **先删菜品再删分类**：如果业务允许，删除分类时级联删除菜品（但通常菜品不应该被删）
5. **分布式锁**：按分类 ID 加锁，串行化删除操作
6. **删除时带条件**：`DELETE FROM category WHERE id = ? AND (SELECT COUNT(*) FROM dish WHERE category_id = ?) = 0`——但 MySQL 不允许在 DELETE 的 WHERE 子查询中引用同一张表的子查询，需要用 JOIN

最稳妥的方案：**加事务 + 删除时用乐观条件**，或者简单点用**分布式锁**。

### 27. DishService.checkAndDeduct() 中有一段合并购物车重复 dishId 的逻辑（LinkedHashMap），这段防御性代码是为了解决什么场景的问题？如果购物车接口已经通过 UNIQUE KEY 保证了不重复，这里还会出现重复吗？

**回答：**

**防御性代码的目的：**
- 代码注释写了："防止购物车历史重复行导致 selectBatchIds size 不匹配"
- 推测是历史遗留问题：早期购物车表可能没有唯一索引，或者从旧系统迁移过来的数据有重复
- 或者是为了兼容"前端可能传重复 dishId"的情况（虽然正常流程不会，但防御性编程）

**有唯一索引还会出现重复吗：**
- 注意：唯一索引是 `uk_user_merchant_dish_spec (user_id, merchant_id, dish_id, spec)`，其中 `spec` 可以为 NULL
- MySQL 的唯一索引中，**NULL 值不参与唯一性比较**，即 `NULL != NULL`，所以 spec 为 NULL 的同一道菜可以重复加入购物车
- 也就是说：**如果菜品没有规格（spec = NULL），唯一索引不生效，购物车中可以有多条相同 dish_id 的记录**

**所以这段代码是必要的！** 它解决了两个问题：
1. spec 为 NULL 时，购物车唯一索引不生效导致的重复行
2. 前端传入的 items 列表中可能有重复 dishId（异常调用、重试等场景）

**更好的修复方式：**
- 购物车表的 spec 字段不要允许 NULL，用空字符串 `''` 表示无规格，这样唯一索引就能正确工作
- 或者调整唯一索引，用生成列或触发器处理 NULL 问题

### 28. 库存扣减使用 Lua 脚本保证原子性，但如果 Redis 执行 Lua 时对应的 key 全部 miss（返回 {-1, key}），代码会 fallback 到 syncStockToRedis() 全量同步一次库存到 Redis，然后再重试。这个 fallback 路径在高并发下单时是否存在 ABA 问题——即 sync 后到重试前之间，库存被其他线程消耗了，Lua 看到的却是旧值？

**回答：**

**ABA 问题分析：**
- 场景：Redis 中某个菜品的库存 key 不存在（可能是过期了，或者从未加载过）
- 线程 A：Lua 返回 -1，开始从 MySQL 同步库存到 Redis（比如库存 100）
- 线程 B：同样发现 key 不存在，也从 MySQL 同步库存到 Redis（也是 100）
- 线程 A：sync 完成，把 Redis 库存设为 100，然后重试 Lua 扣减
- 线程 B：sync 完成，把 Redis 库存也设为 100（覆盖了线程 A 扣减后的结果）
- 结果：线程 A 的扣减被线程 B 的 sync 覆盖了，**超卖！**

**等等，实际要看时序：**
- 如果线程 A sync 完 → 线程 A Lua 扣减（100→99）→ 线程 B sync 完（覆盖成 100）→ 线程 B Lua 扣减（100→99）
- 最终 Redis 库存是 99，但实际应该是 98（两个订单各扣 1）
- **确实有 ABA/覆盖问题**

**但实际发生的概率：**
- 这个 fallback 只在 key miss 时触发，也就是 Redis 启动初期或 key 过期后的第一个请求
- 如果 Redis 稳定运行，key 不会 miss，走不到这个分支
- 但 Redis 冷启动或缓存雪崩时，大量 key 同时失效，这个问题就会集中爆发

**修复方案：**
1. **用 SETNX 保证只有一个线程同步**：发现 key 不存在时，先抢锁，抢到锁的线程从 DB 同步，没抢到的等待后重试
2. **同步也用 Lua**：同步库存时用 Lua 脚本，只有 key 不存在时才 SET，防止覆盖
3. **预热库存**：服务启动时把所有菜品库存加载到 Redis，避免运行时 miss
4. **用原子 INCRBY**：不先同步，而是在 key 不存在时用 `SET key stock NX EX time`，只有设置成功的线程才用新值，失败的重试

### 29. DishService.checkAndDeduct() 中，Redis 扣减成功后，MySQL 同步扣减用的是 setSql 拼接 SQL 字符串。这里存在 SQL 注入风险吗？item.quantity() 是 int 类型不会注入，但如果有其他字段用了 String 拼 SQL，怎么保证？

**回答：**

**当前代码的安全性：**
- `item.quantity()` 是 `int` 类型，拼接数字不会有 SQL 注入风险
- `dishId` 是通过 `.eq(Dish::getId, item.dishId())` 参数化的，不是字符串拼接
- 所以**当前这段代码没有 SQL 注入风险**

**但如果有 String 类型字段拼 SQL，会有注入风险吗？**
- **有**。比如如果用 `setSql("name = '" + name + "'")`，name 中包含单引号就会注入
- MyBatis Plus 的 `setSql()` 是直接拼接 SQL 片段，不会做转义

**如何保证安全：**
1. **优先用 LambdaUpdateWrapper 的 set() 方法**：`.set(Dish::getName, name)`，底层是预编译参数，安全
2. **字符串必须手动转义**：如果不得不用 setSql 拼字符串，用 `StringEscapeUtils.escapeSql()` 或自己转义单引号
3. **代码审查规则**：禁止用字符串拼接的方式在 setSql 中传入用户输入
4. **使用参数化的 setSql**：MyBatis Plus 支持 `${}` 和 `#{}`，但 setSql 里直接写的是 SQL 片段，需要注意
5. **统一封装工具方法**：封装一个安全的 setSql 工具，自动做参数化

**当前代码的改进建议：**
- 虽然 quantity 是 int 安全，但这种写法不好，应该改成：
  ```java
  .setSql("stock = stock - {0}, sales = sales + {1}", item.quantity(), item.quantity())
  ```
  或者用条件构造器的计算方式（但 LambdaUpdateWrapper 不直接支持表达式赋值，只能用 setSql）

### 30. 菜品菜单缓存 MENU_CACHE_PREFIX + merchantId 的过期时间是 10 分钟。如果商家修改了菜品价格或上架了新菜品，用户最长要等 10 分钟才能看到更新。为什么不使用 Redis 的发布订阅或主动淘汰机制？10 分钟的延迟在餐饮行业可接受吗？

**回答：**

**为什么用被动过期而不是主动淘汰：**
- 等等，看代码——其实**已经有主动淘汰了**！
- `DishService.add()`、`update()`、`delete()`、`updateStatus()` 中都调用了 `redisUtil.delete(MENU_CACHE_PREFIX + dish.getMerchantId())`
- 也就是说，菜品变更时会**主动删除缓存**，不是等 10 分钟过期
- 10 分钟 TTL 是兜底方案，防止主动删除失败导致缓存永久不一致

**所以用户最长等待时间不是 10 分钟，而是秒级（等缓存被删除后下次请求回源）。**

**10 分钟延迟在餐饮行业可接受吗：**
- 如果是被动过期（等 TTL），10 分钟不可接受——商家改了价格，用户 10 分钟后才看到新价，可能导致价格争议
- 但如果是主动淘汰（修改即删缓存），用户体验是好的，10 分钟只是兜底
- 餐饮行业对价格和菜品上下架的实时性要求比较高，通常分钟级延迟是上限

**为什么不用 Redis 发布订阅：**
- 单体应用不需要发布订阅。发布订阅通常用于多实例场景，一个实例修改了数据，通过 Redis 发布消息通知其他实例删缓存
- 但当前是单体，直接删本地 Redis 缓存就行
- 如果未来改多实例部署，就需要考虑：
  1. Redis Pub/Sub 广播缓存失效消息
  2. 或者用 Canal 监听 MySQL binlog 异步更新缓存
  3. 或者直接缩短 TTL，接受短暂不一致

### 31. selectMenu 的 SQL 使用 LEFT JOIN t_dish ... AND d.status = 1 AND d.deleted = 0 作为 JOIN 条件。如果分类下所有菜品都被下架了，这个分类还会出现在菜单中吗？这种设计对前端来说是否友好？

**回答：**

**空分类是否会出现在菜单中：**
- **会**。因为是 LEFT JOIN，左表（分类）的数据一定会返回，右表（菜品）不满足条件时返回 NULL
- 即使分类下没有上架的菜品，分类仍然会出现在结果中，只是对应的菜品列表为空

**对前端是否友好：**
- **不友好**。前端展示菜单时，会看到一个分类标题下面没有任何菜品
- 用户可能以为是加载失败，或者觉得奇怪

**两种设计选择：**
1. **显示空分类**：让用户知道有这个分类但暂时没菜（比如早餐类过了早餐时间全部下架）
2. **不显示空分类**：菜单更干净，用户看不到没菜的分类

**餐饮场景通常的做法：**
- 不显示空分类，因为分类的意义就是组织菜品，没菜品的分类没有展示价值
- 但也有例外，比如商家设置了"即将上线"分类

**如何修改：**
- 如果想过滤空分类，改成 INNER JOIN，或者在 WHERE 中加 `d.id IS NOT NULL`（但这样就不是 LEFT JOIN 了）
- 或者在 Java 代码中过滤掉没有菜品的分类
- 当前代码返回的是 List<MenuCategoryVO>，每个分类下有 List<MenuDishVO>，如果 list 为空前端应该能处理

### 32. DishRequest 中用 record 类型定义 DTO，而 Merchant 模块用的是 class——这种不一致是什么原因？record 和 class 在参数校验时（@Valid）的行为有区别吗？

**回答：**

**不一致的原因：**
- 推测是**渐进式重构**或**不同开发者的习惯**
- record 是 Java 16+ 的新特性，可能某些模块的开发者更熟悉新特性，先在 Dish 模块试用
- 或者 Dish 模块是后写的，用了更新的编码风格
- 没有强制的代码规范导致模块间风格不一致

**record 和 class 在 @Valid 校验时的行为区别：**
1. **注解位置不同**：
   - class 中注解写在字段上：`@NotBlank private String name;`
   - record 中注解写在构造器参数上：`record DishRequest(@NotBlank String name) {}`
   - 或者用 `@get:NotBlank` 显式指定注解放在 getter 上

2. **校验功能基本一致**：
   - 只要注解位置正确（能被 Validation API 识别），校验行为是一样的
   - Bean Validation 2.0+ 支持 record 的校验

3. **Spring 中的行为**：
   - Spring 对 record DTO 的参数绑定和校验是支持的（Spring Boot 2.6+）
   - `@RequestBody` + `@Valid` 对 record 和 class 的行为一致
   - 但 `@ModelAttribute`（表单提交）在某些版本下对 record 的支持可能有问题

**record 的其他限制：**
- 字段是 final 的，不能 set
- 不能继承其他类
- 不能有额外的实例字段（构造器参数即全部字段）
- 优点：代码简洁，不可变，自动生成 equals/hashCode/toString

---

## 六、订单模块（Cart + Order + MockPay）

### 33. 购物车的 UNIQUE KEY uk_user_merchant_dish_spec 包含了 spec 字段，但 spec 可为 NULL。MySQL 中唯一索引允许 NULL 值重复，如果某菜品没有规格（spec IS NULL），用户能加入两条相同的记录吗？这违反了设计意图吗？

**回答：**

**MySQL 唯一索引的 NULL 行为：**
- MySQL 的 B-Tree 唯一索引中，**NULL 值被认为是不相等的**，即 `NULL != NULL`
- 所以唯一索引允许存在多行 `spec IS NULL` 的记录
- 结论：**如果菜品没有规格（spec = NULL），用户可以加入两条相同 dishId 的记录**

**是否违反设计意图：**
- **违反**。设计意图应该是"同一个用户、同一个商家、同一道菜、同一种规格只能有一条购物车记录"
- 无规格的菜品（spec 为 NULL）应该被视为"只有一种规格"，应该是唯一的

**为什么会有这个问题：**
- 开发者可能不知道 MySQL 唯一索引对 NULL 的特殊行为
- 或者觉得有规格的菜品占多数，无规格的情况少，没在意

**修复方案：**
1. **用空字符串代替 NULL**：spec 字段 NOT NULL DEFAULT ''，无规格时存空字符串，这样唯一索引就能生效
2. **生成列 + 唯一索引**：`ALTER TABLE t_cart ADD spec_unique VARCHAR(64) GENERATED ALWAYS AS (IFNULL(spec, '')) VIRTUAL, ADD UNIQUE KEY uk_user_merchant_dish_spec_unique (user_id, merchant_id, dish_id, spec_unique)`
3. **应用层防御**：在 CartService.addToCart() 中先查询，存在则更新数量，不存在才插入——但并发下仍可能有问题（需要分布式锁或事务 + SELECT FOR UPDATE）
4. **使用 INSERT ... ON DUPLICATE KEY UPDATE**：但前提是唯一索引起作用，所以还是得解决 NULL 的问题

推荐方案 1：把 spec 默认值改为空字符串，NOT NULL，简单直接。

### 34. CartService.addToCart() 先查 existing 再决定 insert/update，这不是原子操作。并发下同一用户同时点击两次"加入购物车"，会出现两条相同 dish+spec 的记录吗？怎么解决？

**回答：**

**并发下是否会出现两条相同记录：**
- **取决于 spec 是否为 NULL 和唯一索引是否生效**（见上一题）
- 如果 spec 不为空且唯一索引生效：第二次插入会报 DuplicateKeyException，不会出现两条
- 如果 spec 为 NULL（唯一索引对 NULL 不生效）：两次插入都成功，**出现两条重复记录**
- 即使 spec 不为空，先查后插的方式在并发下也会有"两次都查不到 → 两次都插入 → 第二次因为唯一索引报错"的情况，只是不会有重复数据，但会有异常

**怎么解决：**

1. **利用唯一索引 + 异常捕获**：
   - 先尝试 INSERT，如果报 DuplicateKeyException，说明已经存在，再执行 UPDATE（增加数量）
   - 这是"乐观"的方式，适合写冲突少的场景
   - 但 spec 为 NULL 时唯一索引不管用，所以得先解决上一题的问题

2. **分布式锁**：
   - 按 `userId + merchantId + dishId + spec` 加 Redis 锁
   - 同一道菜的加入购物车操作串行化
   - 性能略低，但安全可靠

3. **数据库层面用 INSERT ... ON DUPLICATE KEY UPDATE**：
   - 一条 SQL 完成"不存在则插入，存在则更新数量"
   - 性能最好，原子性由数据库保证
   - 同样依赖唯一索引，需要先解决 NULL 问题

4. **事务 + SELECT ... FOR UPDATE**：
   - 在事务内先 `SELECT ... FOR UPDATE` 锁定行
   - 但如果行不存在，FOR UPDATE 锁不住（需要 Gap Lock，RR 级别下有）
   - 复杂度高，不推荐

**推荐方案：** 解决 NULL 问题（用空字符串代替） + INSERT ON DUPLICATE KEY UPDATE，最简洁高效。

### 35. OrderService.submit() 方法非常长（~90 行），做了一个大事务完成所有操作：校验→扣库存→计算价格→写订单→写明细→清购物车→标记优惠券使用。这个大事务在库存扣减成功但订单写入失败时能正确回滚 Redis 中已扣减的库存吗？@Transactional 能回滚 Redis 操作吗？

**回答：**

**@Transactional 能回滚 Redis 操作吗？**
- **不能**。`@Transactional` 只管理数据库事务（DataSourceTransactionManager），不管理 Redis 操作
- 事务回滚时，已经执行的 Redis 命令（扣减库存）不会自动回滚

**库存扣减成功但订单写入失败时会怎样：**
- 流程：扣 Redis 库存 → 扣 MySQL 库存 → 写订单 → ...
- 如果写订单时失败抛出异常，`@Transactional` 回滚：
  - MySQL 的库存扣减会回滚 ✓
  - MySQL 的订单写入会回滚 ✓
  - Redis 的库存扣减**不会回滚** ✗
- 结果：Redis 中库存少了，但实际没有对应的订单，**库存不一致**（Redis 库存比实际少）

**等等，看代码实际顺序：**
```
checkAndDeduct() {
    Lua 扣 Redis 库存
    MySQL 扣库存（setSql）
}
→ 写订单
→ 写明细
→ 清购物车
→ markUsed 优惠券
```
- checkAndDeduct() 内部先扣 Redis 再扣 MySQL，都在 submit 的大事务里
- 如果后面写订单失败，MySQL 回滚（库存加回去），但 Redis 不会回滚
- **确实有数据不一致问题**

**如何解决：**
1. **补偿机制**：在 catch 块中手动回滚 Redis 库存（调用 revertStock）
2. **后置 Redis 方案**：先扣 MySQL 库存，事务提交成功后再扣 Redis 库存——但这样超卖了怎么办？
3. **事务消息/可靠事件**：MySQL 事务提交后发消息，消费者异步扣 Redis，最终一致
4. **Redis 也加入事务**：用 SessionCallback 把 Redis 操作也做成事务型？但 Redis 事务和 MySQL 事务不是同一个事务管理器，还是有问题
5. **TCC 模式**：Redis 和 MySQL 都做 Try-Confirm-Cancel，太重了

**当前项目的实际风险：**
- 写订单失败的概率不高（字段足够，不是唯一键冲突），所以这个问题出现概率低
- 但一旦发生，Redis 库存会偏少，相当于用户看到的库存比实际少，不会超卖，只是少卖了
- 可以通过定时任务同步 MySQL 库存到 Redis 来补偿（每天凌晨校准一次）

### 36. OrderService.cancel() 使用 Redis 分布式锁（setIfAbsent + 30 秒自动释放），但如果业务逻辑执行时间超过 30 秒，锁自动释放后另一个线程进入，会发生什么？finally 块中的 redisTemplate.delete(lockKey) 会删除后一个线程的锁吗？这是否是一个标准的锁超时问题？

**回答：**

**锁超时后的场景：**
- 线程 A 获取锁，开始执行取消逻辑
- 业务执行超过 30 秒，锁自动过期释放
- 线程 B 获取到同一把锁，开始执行
- 线程 A 终于执行完，在 finally 中执行 `delete(lockKey)`
- 线程 A 删除的是**线程 B 的锁**！
- 线程 B 还在执行，但锁已经没了，线程 C 又可以获取锁
- **结果：多个线程同时执行，锁失效，可能导致并发问题**

**这是标准的分布式锁超时问题吗？**
- **是**。这就是经典的"锁过期导致的并发安全问题"和"误删他人锁"问题

**如何解决：**

1. **锁值标识 + 释放校验**：
   - 加锁时 value 设为唯一标识（如 UUID + 线程 ID）
   - 释放锁时先校验 value 是否是自己的，是自己的才删除
   - 用 Lua 脚本保证"校验+删除"的原子性

2. **锁续期（看门狗）**：
   - 业务执行时间长时，后台线程定时给锁续期（比如每 10 秒续一次，续到 30 秒）
   - Redisson 的看门狗就是这种机制

3. **合理设置超时时间**：
   - 评估业务最长执行时间，设置更保守的超时时间（如 60 秒、5 分钟）
   - 但不能从根本解决问题，只是降低概率

4. **幂等设计**：
   - 即使多个线程同时进入，也不会造成数据错误（比如用 CAS 更新状态）
   - 代码中的 `update ... WHERE status IN (1,2)` 其实已经有 CAS 的意味，所以即使并发也不会有严重问题（最多是重复回滚库存？）

**当前代码的实际影响：**
- 取消订单的业务逻辑不重（查询+更新+回滚库存+退券），正常应该在几百毫秒内完成，30 秒超时很难触发
- 但如果数据库或 Redis 慢查询、网络抖动，极端情况下还是可能超过 30 秒
- 虽然有 CAS 更新兜底不会出现数据错乱，但"误删他人锁"是一个不优雅的实现

### 37. OrderService.updateStatusWithLock() 同样存在上述锁超时问题，而且 @Transactional 事务注解在方法上，锁获取在事务开始之前，锁释放可能在事务提交之前——这会导致什么时序问题？

**回答：**

**锁和事务的时序问题：**

**Spring 的事务实现是 AOP 代理：**
- `@Transactional` 方法的执行顺序：
  1. 开启事务（TransactionInterceptor 前置通知）
  2. 执行方法体（包括获取锁、业务逻辑、释放锁）
  3. 提交事务（TransactionInterceptor 后置通知）

**所以实际时序是：**
```
开启事务 → 获取锁 → 执行业务 → 释放锁 → 提交事务
```

**问题出在"释放锁 → 提交事务"之间的时间窗口：**
- 线程 A 释放了锁，但事务还没提交
- 线程 B 获取到锁，读取订单状态（还是旧状态，因为 A 的事务还没提交）
- 线程 A 提交事务，状态更新
- 线程 B 执行 CAS 更新（WHERE status = expected），因为 A 已经改了状态，所以 B 更新 0 行 → 抛出异常
- 结果：B 线程报错，业务上是正确的（并发下只有一个能成功），但用户体验不好（"订单状态已变更"）

**更严重的问题（如果没有 CAS）：**
- 如果 updateStatusWithLock 里没有用 `WHERE status = expected` 做 CAS，而是直接 set status
- 那 B 线程读到的是旧状态，修改后提交，会覆盖 A 的修改（丢失更新）
- 但当前代码有 CAS，所以数据是安全的，只是体验问题

**正确的时序应该是：**
```
获取锁 → 开启事务 → 执行业务 → 提交事务 → 释放锁
```

**如何实现：**
- 把事务范围缩小：锁在方法外获取，事务在锁内的方法上（即把事务方法拆成另一个方法，在锁内部调用）
- 或者用 TransactionTemplate 手动控制事务边界，确保在锁内开启和提交

**为什么现在的写法有问题还能工作：**
- 因为有 CAS 兜底（WHERE status = expected），所以即使事务在锁外提交，也不会出现脏写
- 只是性能和用户体验稍差（并发冲突时抛异常）

### 38. 模拟支付接口 /api/pay/callback 允许指定 success 参数为 false，直接支付失败。但在生产环境替换真实支付网关后，这个接口会被移除还是保留？如果保留，如何防止恶意调用者通过 /api/pay/callback 接口伪造支付成功回调？

**回答：**

**生产环境会保留吗：**
- 真实支付网关接入后，MockPayController 应该移除或用 `@Profile("!prod")` 禁用
- 但真实支付也需要回调接口，只是实现会换成真实支付的验签逻辑

**如果保留 Mock 接口的风险：**
- 攻击者知道回调接口后，可以直接调用 `POST /api/pay/callback?orderNo=xxx&success=true` 把任意订单改成支付成功
- 不需要付钱就能"支付成功"，严重的资金漏洞

**防止伪造回调的方法（真实支付场景）：**

1. **签名验证**：
   - 支付平台（如支付宝、微信支付）的回调请求会携带签名，用商户密钥对请求参数签名
   - 后端收到回调后，用相同的密钥和算法验签，确认是支付平台发来的
   - 这是最核心的安全措施

2. **回调 IP 白名单**：
   - 只允许支付平台的官方 IP 段访问回调接口
   - 但 IP 可能变动，维护成本高，通常作为辅助手段

3. **订单状态校验**：
   - 回调时校验订单金额是否一致、订单状态是否为待支付
   - 防止用低金额订单的回调伪造高金额订单

4. **幂等处理**：
   - 重复回调不会重复处理，保证幂等
   - 用订单号 + 支付流水号做幂等键

5. **HTTPS**：
   - 回调接口必须用 HTTPS，防止中间人篡改

**当前 Mock 接口的问题：**
- 没有任何安全校验，直接根据 success 参数改状态
- 生产环境必须替换成真实支付的验签逻辑
- 建议用 `@Profile("dev")` 或 `@ConditionalOnProperty` 控制 Mock 支付只在开发环境启用

### 39. MockPayController.create() 中，创建支付单和订单状态校验之间没有将 @Transactional 作用在整个方法上（方法上没有 @Transactional）。如果 SnowflakeIdUtil.generate() 生成 ID 后写入 Redis 成功，但方法中途返回异常，Redis 中会残留未完成的支付记录吗？

**回答：**

**Redis 中会残留支付记录吗：**
- **会**。Redis 操作不参与数据库事务，如果方法中途异常返回，已经写入 Redis 的支付数据不会自动回滚
- 但支付记录有过期时间吗？如果有 TTL，过期后会自动清理
- 如果没有 TTL，就会永久残留（垃圾数据）

**这有什么影响：**
- 垃圾数据占用 Redis 内存
- 如果支付记录用于查询支付状态，用户可能查到一个从未完成的支付单
- 但因为是 Mock 支付，影响有限

**更严重的问题是订单状态校验和支付创建的原子性：**
- 方法逻辑：校验订单状态 → 生成支付单 → 存 Redis
- 如果没有事务，并发下两个线程都通过了状态校验（都是待支付），都创建了支付单
- 虽然订单状态更新时有 CAS，但支付单可能创建了多个
- 不过 Mock 支付场景下无所谓

**如何改进：**
1. **给 Redis 的支付记录加 TTL**：比如 30 分钟过期，自动清理垃圾数据
2. **加分布式锁**：按 orderNo 加锁，同一订单的支付创建串行化
3. **用数据库事务 + 事务消息**：先在数据库创建支付记录（事务内），提交后同步到 Redis
4. **Redis 也做幂等**：用 SETNX 创建支付单，如果已存在则返回已创建

**生产环境的真实支付：**
- 真实支付应该有数据库支付流水表，和订单更新在同一个事务里
- Redis 只是缓存或辅助，数据库才是权威

### 40. 订单取消时调用了 revertOrderStock() 回滚库存，但回滚使用的是 RedisTemplate.opsForValue().increment() 增加库存，而不是通过 Lua 脚本。如果取消和下单并发执行，会出现库存"超卖"或"超回"吗？

**回答：**

**回滚用 increment 的并发安全性分析：**

**场景 1：取消和下单并发（不同订单，同一道菜）**
- 线程 A：下单，Lua 扣减库存（100 → 99）
- 线程 B：取消订单，increment 回滚库存（99 → 100）
- 这两个操作都是原子的（INCR 是 Redis 原子命令，Lua 也是原子的）
- 最终结果取决于时序，但不会出现错误值
- **不会超卖也不会超回**

**场景 2：同一个订单，取消和支付并发**
- 这是业务层面的并发，不是库存操作的并发
- 代码中 cancel() 和 payOrder() 都用了分布式锁 `lock:order:{orderNo}`，所以同一订单的操作是串行的
- 不会有问题

**场景 3：多次取消同一个订单**
- 如果因为重试或 bug，cancel 被调用两次
- 第一次：库存 +1（正确）
- 第二次：库存又 +1（错误，超回了）
- 但 cancel 中有状态校验（只有待支付/待接单才能取消）和 CAS 更新（WHERE status IN (1,2)），第二次取消会因为状态不对而失败，不会走到回滚库存那一步
- 所以**不会超回**

**为什么回滚不用 Lua：**
- 扣减用 Lua 是因为需要"检查库存是否足够 + 扣减"两个操作的原子性
- 回滚只是简单的增加库存，INCR 命令本身就是原子的，不需要 Lua
- 如果回滚需要检查"回滚后不超过原始库存"之类的上限，才需要 Lua

**潜在问题：**
- 如果 Redis 中库存 key 不存在（比如过期了），INCR 会从 0 开始加，变成正数
- 但正常流程下库存 key 应该存在（下单时扣过），除非中间过期了
- 这种边界情况下，回滚后的库存可能不准确（比实际多），但不会超卖（只是少卖）

---

## 七、优惠券模块（Coupon）

### 41. CouponService.receive() 使用 Redis 分布式锁（10 秒超时）防止重复领取，但最后的 finally 中释放锁没有校验是否是自己的锁——如果方法执行时间超过 10 秒，锁自动释放后另一个线程进入，finally 释放的可能是另一个线程的锁。这会导致什么问题？

**回答：**

**会导致的问题：**

1. **锁误删除**：线程 A 执行超过 10 秒，锁自动释放；线程 B 获取锁；线程 A 执行完 finally 中 delete，删除了 B 的锁
2. **锁失效**：B 的锁被删后，线程 C 又能获取锁，导致多个线程同时执行领券逻辑
3. **多发优惠券**：没有锁保护后，`couponMapper.incrementReceivedCount()` 的乐观锁可能顶不住（见下一题），导致超发

**领券场景 10 秒会超时吗：**
- 正常领券逻辑应该很快（查优惠券、查已领、UPDATE 自增、INSERT 用户优惠券），几十毫秒足够
- 10 秒超时一般不会触发，除非数据库极度慢或网络分区
- 但低概率 ≠ 不会发生，生产环境还是要做严谨

**修复方案：**
1. **锁值设为唯一标识**：加锁时 value = UUID，释放时先 GET 校验是自己的再 DELETE（用 Lua 保证原子）
2. **锁续期**：用 Redisson 等带看门狗的客户端，自动续期
3. **增加超时时间**：10 秒改 60 秒，降低概率（但不根治）
4. **数据库兜底**：即使锁失效，数据库层面也能防超发（见下一题的 incrementReceivedCount 乐观锁）

**当前代码的数据库兜底：**
- `couponMapper.incrementReceivedCount(couponId)` 如果是 `UPDATE t_coupon SET received_count = received_count + 1 WHERE id = ? AND received_count < total_count`，这是乐观锁
- 即使锁失效了，数据库的行锁 + 条件也能保证不会超发（高并发下最后一个名额可能有多个线程抢，但只有一个能成功）
- 所以最终数据是安全的，只是并发冲突时有些线程会领券失败（"优惠券已领完"）

### 42. CouponMapper.incrementReceivedCount() 的实现是什么？如果使用 UPDATE ... WHERE received_count < total_count 这种乐观锁方式，高并发下最后一个名额被多线程同时抢到会发生什么？

**回答：**

**incrementReceivedCount 的实现推测：**
```sql
UPDATE t_coupon 
SET received_count = received_count + 1 
WHERE id = #{couponId} 
  AND received_count < total_count
  AND status = 1
```
返回受影响行数（0 或 1）。

**高并发下最后一个名额的情况：**
- 假设 received_count = 99，total_count = 100，只剩最后一个名额
- 100 个线程同时执行这条 UPDATE
- MySQL InnoDB 的行锁会把这些 UPDATE 串行化
- 第一个线程：条件满足（99 < 100），更新成功，received_count = 100，返回 1
- 第二个线程：条件不满足（100 < 100 为假），更新 0 行，返回 0
- 第 N 个线程：同上，返回 0
- **结果：只有 1 个线程成功，不会超发**

**那为什么还需要分布式锁：**
- 没有锁的情况下，所有请求都会打到数据库，高并发下数据库压力大
- 分布式锁起到"流量削峰"的作用，把大部分请求挡在 Redis 层，只有拿到锁的请求才查数据库
- 锁是第一道防线，数据库乐观锁是最后一道防线

**乐观锁的性能问题：**
- 高并发下，大量线程竞争同一行的行锁，会导致很多锁等待
- 极端情况下会出现大量超时或死锁
- 所以 Redis 锁是有必要的，可以减轻数据库压力

**ABA 问题：**
- 如果有领券也有退券（received_count 增减），可能出现 ABA，但优惠券一般领了不会退回到券池（退券是退用户优惠券，不是减少 received_count）
- 当前代码中的 refund() 是把用户优惠券状态改回未使用，不是 received_count - 1，所以没有 ABA

### 43. 优惠券的过期时间（validEnd）只在校验时比较 LocalDateTime.now()，但如果 Redis 服务器时间与 MySQL 服务器时间不一致，或者服务器时区配置错误，会导致什么后果？为什么不用数据库时间 NOW()？

**回答：**

**时间不一致导致的后果：**

1. **优惠券提前过期**：应用服务器时间比真实时间快，用户还没到过期时间就不能用了
2. **优惠券延期有效**：应用服务器时间比真实时间慢，过期了还能用，商家损失
3. **跨时区问题**：服务器时区配置错误（如默认 UTC 而不是 Asia/Shanghai），时差 8 小时，影响更大

**为什么不用数据库时间 NOW()：**

1. **性能考虑**：每次校验都查数据库会增加 DB 压力，当前是从用户优惠券表查出来后在 Java 代码中比较，只需要一次查询
2. **一致性问题**：优惠券列表查询和校验应该用同一个时间基准，如果列表用了 Java now()，校验用了 DB now()，可能出现"列表显示有券但用不了"的情况
3. **开发方便**：Java 8 的 LocalDateTime API 用起来方便

**但用应用服务器时间的风险：**
- 应用服务器集群如果时间不同步，A 机器上能领的券 B 机器上不能领
- NTP 同步可以减小误差，但不能完全消除

**最佳实践：**
1. **统一时间源**：所有服务器都配置 NTP 同步，保证时间误差在秒级以内
2. **数据库时间为准**：涉及过期、有效期等关键业务判断，优先用数据库时间（`NOW()` 或 `CURRENT_TIMESTAMP`），因为数据库通常是单点（或主从一致），时间统一
3. **时间精度要求不高的用应用时间**：比如显示用、缓存过期等，几秒钟误差可接受

**当前场景：**
- 优惠券过期时间的精度要求不高（差个几秒甚至几分钟用户都能接受）
- 用 `LocalDateTime.now()` 问题不大，只要服务器配置了正确的时区和 NTP 同步
- 但要注意：应用服务器和数据库服务器的时区必须一致，都设为 Asia/Shanghai

### 44. CouponService.getUsable() 中条件 .ge(UserCoupon::getValidEnd, now) 是 validEnd >= now，但优惠券的语义通常是"在 validEnd 时刻之前使用"，优惠券应该在 validEnd 当天 23:59:59 过期还是 validEnd 00:00:00 过期？现有实现是哪种？

**回答：**

**优惠券过期时间的两种语义：**

1. **"当天有效"语义**：validEnd = 2024-07-20 表示 7 月 20 日当天 23:59:59 前都有效
2. **"精确时刻"语义**：validEnd = 2024-07-20 00:00:00 表示到 7 月 20 日 0 点就过期（即 7 月 19 日 23:59:59 前有效）

**现有实现：**
- 代码：`.ge(UserCoupon::getValidEnd, now)` → `validEnd >= now`
- validEnd 字段是 `DATETIME` 类型（LocalDateTime）
- 数据库中存的是什么？比如 `2024-07-31 23:59:59` 还是 `2024-07-31 00:00:00`？
- 从领券代码看：`uc.setValidEnd(coupon.getValidEnd());`，直接复制模板的 validEnd
- 从优惠券模板的语义看，运营设置的"有效期至 7 月 31 日"通常意味着 7 月 31 日当天还能用
- 所以如果 validEnd 存的是 `2024-07-31 00:00:00`，那 `.ge` 判断的话，7 月 31 日 0 点之后就不能用了，不符合预期
- 如果 validEnd 存的是 `2024-07-31 23:59:59`，那 `.ge` 判断的话，7 月 31 日整天都能用，23:59:59 之后过期，也符合预期

**现有代码的问题：**
- 取决于运营怎么录入 validEnd。如果录入的是日期（只有年月日，没有时分秒），默认 00:00:00，那就是当天 0 点过期，用户少了一整天
- 更安全的做法是用 `validEnd.toLocalDate()` 比较，或者明确 validEnd 的语义是"截止到当天的 23:59:59"

**业界惯例：**
- 优惠券通常是"当天有效"，即 validEnd 当天 23:59:59 前可用
- 实现方式：
  1. validEnd 存当天 23:59:59（精确时刻方式）
  2. 或 validEnd 存日期，比较时用 `validEnd.toLocalDate().isAfter(now.toLocalDate())` + 当天有效
- 推荐方式 2，更直观，不容易出错

### 45. 退券（CouponService.refund()）方法捕获了 Exception 并只打了 warn 日志。如果退券失败，用户支付时已使用优惠券但订单取消后优惠券没退回，这个 Bug 会被日志发现吗？现有监控体系能发现这种静默失败吗？

**回答：**

**会被日志发现吗：**
- **会，但需要有人看 warn 日志**。退券失败会打 `log.warn("退券失败，userCouponId={}", ...)`
- 但如果没人关注 warn 级别的日志，或者日志量太大被淹没，就发现不了
- 业务上用户也可能发现（"我的券怎么没退回来"），然后找客服

**现有监控体系能发现吗：**
- **不能**。当前项目没有任何监控体系（没有 Actuator、没有 Prometheus、没有告警）
- 这是典型的"静默失败"——错误发生了但没有任何人/系统知道

**这种静默失败的影响：**
- 用户体验差：取消订单后优惠券没退回，用户以为系统吞券
- 客诉增加
- 严重时会被认为是欺诈（"故意不退券"）

**改进方案：**

1. **提高日志级别**：退券失败应该是 error 级别而不是 warn，因为这是影响用户资产的操作
2. **增加 metrics 监控**：用 Micrometer 统计退券失败次数，配置告警阈值
3. **重试机制**：退券失败后自动重试几次（但要注意幂等）
4. **对账补偿**：定时任务扫描"已取消但优惠券未退回"的订单，自动退券
5. **事务消息**：订单取消和退券用可靠消息保证最终一致
6. **退券失败时前端提示**：取消订单成功但退券失败，告诉用户"优惠券将在稍后退回"，而不是悄无声息

**为什么代码里用 try-catch 吞异常：**
- 可能是担心退券失败影响订单取消的主流程（订单取消是主流程，退券是次要的）
- 但"降级处理"不等于"不管了"，应该有补偿机制

---

## 八、评价模块（Review）

### 46. 提交评价后，ReviewService.submit() 中实时计算 avgScoreByMerchant 并更新商家评分。如果某个商家收到大量刷评（比如 10000 条 1 分），每次提交都重新计算 AVG 并 UPDATE，这个性能开销能接受吗？为什么不用定时任务异步聚合评分？如果有两个评价同时提交，avgScoreByMerchant + updateScore 的组合操作存在竞态条件吗？

**回答：**

**实时计算 AVG 的性能开销：**
- 每次评价都做一次 `SELECT AVG(score) FROM t_review WHERE merchant_id = ?`
- 如果 t_review 有 `idx_merchant_id` 索引，AVG 可以走索引扫描，但仍然要扫描该商家的所有评价行
- 1 万条评价的话，单次 AVG 可能几十毫秒，勉强能接受
- 10 万条评价的话，会慢到百毫秒级，高并发下数据库压力大
- 刷评场景下，每秒几十上百条评价，数据库会被打满

**为什么不用定时任务异步聚合：**
- 实时性要求：用户提交评价后希望立即看到评分变化
- 实现简单：直接查 AVG 比定时任务+缓存简单
- 初期数据量小，性能没问题

**两个评价同时提交的竞态条件：**
- 线程 A：计算 AVG → 4.5 分
- 线程 B：计算 AVG → 4.5 分（还没看到 A 的评价，因为事务隔离级别）
- 线程 A：更新商家评分 4.5
- 线程 B：更新商家评分 4.5
- 结果：两条评价后评分还是 4.5？不对，应该是两条评价后的新 AVG
- 等等，要看事务隔离级别和实际执行顺序：
  - 如果是 RC 级别，两个事务都能读到对方未提交的评价吗？不能
  - 所以 A 和 B 计算 AVG 时都不包含对方的评价
  - A 先提交，更新评分为 A算的AVG（包含A自己，因为 A 事务内能看到自己的插入）
  - B 后提交，更新评分为 B算的AVG（包含B自己，但看不到A）
  - 最终评分只包含 B 的评价，A 的评价被"丢失"在评分计算外了
- **结论：存在竞态条件，可能导致评分不准确**

**但实际上：**
- submit() 方法上有 `@Transactional`，但没有加锁
- 如果两个并发评价同时提交，商家评分可能不准确（少算）
- 误差范围通常很小（一两条评价的差距），下次有人评价时可能又被修正回来

**正确方案：**
1. **定时任务聚合**：每 5 分钟/每小时重新计算所有商家的评分，异步更新
2. **增量更新**：不重新计算 AVG，而是用公式：`new_avg = (old_avg * old_count + new_score) / (old_count + 1)`，用一条原子 UPDATE 完成
3. **Redis 聚合**：评分放 Redis，异步同步到 MySQL

### 47. ReviewMapper.avgScoreByMerchant() 使用 SELECT AVG(score) 聚合查询，如果 t_review 表在商家爆单时有上百万条评价数据，这个查询对 InnoDB 来说需要全表扫描吗？需要加什么索引？

**回答：**

**是否需要全表扫描：**
- **取决于索引**。如果有 `idx_merchant_id (merchant_id)` 索引：
  - MySQL 可以通过索引定位到该商家的所有评价行
  - 但 AVG(score) 仍然需要扫描所有该商家的评价行（索引回表或索引覆盖）
  - 如果有 `(merchant_id, score)` 复合索引，可以直接用索引覆盖（Using index），不需要回表，性能提升明显
- 如果没有任何索引：
  - 全表扫描，百万数据下很慢

**需要加什么索引：**
- `idx_merchant_id (merchant_id)` —— 最基本的，应该已经有了
- `idx_merchant_id_score (merchant_id, score)` —— 覆盖索引，优化 AVG 查询
- 但要注意：索引越多写入越慢，评价表写多读也多，需要权衡

**百万评价下的性能：**
- 即使有覆盖索引，一个商家有 10 万条评价，AVG 也需要扫 10 万行索引
- 单次查询可能几十到上百毫秒
- 如果 QPS 高（比如每秒几十次评价提交，每次都算 AVG），数据库压力很大

**更好的方案：**
1. **预聚合**：在商家表冗余 `review_count` 和 `total_score` 字段，每次评价时原子更新这两个字段，评分 = total_score / review_count
2. **Redis 缓存**：商家评分存在 Redis，提交评价时更新 Redis，定时同步到 MySQL
3. **异步计算**：提交评价后发消息，消费者异步更新评分

### 48. 评价必须先校验订单已完成（status == 6）、再校验未评价过（SELECT COUNT(*)）、最后 INSERT。这三步操作在同一个 @Transactional 中，但第三步 INSERT 可能因为 UNIQUE KEY uk_order_no 约束而失败——这个唯一约束能替代前面的 SELECT COUNT(*) 校验吗？

**回答：**

**唯一约束能替代 SELECT COUNT(*) 吗？**
- **部分能，但不完全能**。

**能替代的部分：**
- 防重复评价的功能上，唯一索引更可靠（并发下也能保证不重复）
- SELECT COUNT(*) 是"检查再执行"，并发下可能两个线程都检查通过，都插入成功（如果没有唯一索引）
- 有了唯一索引，第二次插入会报 DuplicateKeyException，保证数据不重复

**不能替代的部分：**
1. **错误信息不友好**：唯一索引冲突报的是 SQL 异常，需要捕获并转换成"该订单已评价"的业务异常
2. **违反"异常做流程控制"的最佳实践**：用异常控制正常业务流程，性能和可读性都不好
3. **其他校验可能需要先执行**：比如订单状态校验、用户权限校验，如果这些校验和重复评价一起，用异常的话你不知道是哪个原因失败的

**当前代码的问题：**
- 有 SELECT COUNT(*) 也有唯一索引，是双重校验
- 但 SELECT COUNT(*) 在高并发下是多余的（不能防并发重复），唯一索引才是最终保障
- 反而增加了一次数据库查询

**推荐方案：**
1. **保留唯一索引**（数据库层面的最终保障）
2. **保留 SELECT COUNT(*)**（快速返回友好的错误信息，减少唯一键冲突的概率）
3. **捕获 DuplicateKeyException** 作为兜底，转换成业务异常
4. 这是"乐观检查 + 数据库兜底"的常见模式

**或者完全用异常驱动：**
- 去掉 SELECT COUNT(*)，直接 INSERT
- 捕获 DuplicateKeyException，转换成"已评价"异常
- 代码更简洁，但性能略差（异常创建开销），且不符合 Java 最佳实践
- 适合写冲突少的场景

---

## 九、收藏模块（Favorite）

### 49. FavoriteService.add() 通过捕获 DuplicateKeyException 来实现幂等，这种"用异常做流程控制"在 Java 最佳实践中通常被认定为 anti-pattern。为什么不用 SELECT COUNT(*) 先检查？异常创建堆栈的开销在高频收藏场景下可以忽略吗？

**回答：**

**为什么不用 SELECT COUNT(*) 先检查：**
- SELECT + INSERT 不是原子操作，并发下两个请求都检查到不存在，都尝试插入，一个成功一个失败
- 如果用 SELECT COUNT(*)，还需要处理并发冲突的问题，最终还是要靠唯一索引兜底
- 用 DuplicateKeyException 的方式代码更简洁（一次数据库操作搞定）

**为什么说这是 anti-pattern：**
1. **异常的设计目的是处理意外情况**，不是正常业务流程
2. **性能开销**：创建异常需要填充堆栈信息，开销比正常返回大
3. **可读性差**：看代码不知道"重复收藏"是正常情况还是异常情况
4. **调试困难**：大量预期内的异常会干扰真正的错误排查

**高频场景下的开销可以忽略吗：**
- **取决于频率**。如果每秒几十次重复收藏，异常开销不可忽略
- 但收藏场景下，重复收藏的概率通常不高（用户不会反复收藏同一个商家）
- 实际性能测试：异常创建的开销大约是正常返回的 10-100 倍，但绝对时间也只是微秒级，除非 QPS 特别高否则影响不大

**更好的实践：**

1. **先查后插（适合写冲突少的场景）**：
   - 先 SELECT，如果已存在直接返回
   - 再 INSERT，捕获 DuplicateKeyException 做兜底
   - 优点：正常路径没有异常，性能好
   - 缺点：多一次数据库查询

2. **INSERT ... ON DUPLICATE KEY UPDATE（如果是更新语义）**：
   - 但收藏是幂等操作（重复收藏等于已收藏），不需要 UPDATE
   - 可以 UPDATE 一个不重要的字段（如 updated_at），但没必要

3. **Redis 去重**：
   - 收藏前先查 Redis 中是否有记录
   - 有则直接返回，没有则 INSERT + 写 Redis
   - 减少数据库压力

**当前代码的合理性：**
- 收藏操作频率不高，重复收藏的概率更低
- 用 DuplicateKeyException 实现简单，代码量少
- 虽然是 anti-pattern，但在这种低频场景下是可以接受的权衡

### 50. Favorite 表无 deleted 字段（物理删除），其余表全部是逻辑删除。为什么收藏模块采用了不同的删除策略？这种设计不一致会给后续统一的数据归档/清理带来什么困难？

**回答：**

**为什么收藏用物理删除：**

推测原因：
1. **收藏数据不重要**：收藏是用户的偏好标记，删了就删了，不需要恢复
2. **数据量大**：收藏数据可能很多（用户可以收藏很多商家），逻辑删除会占用更多空间
3. **查询简单**：物理删除后查询不需要加 deleted = 0 条件
4. **开发习惯**：不同开发者的实现习惯不同

**实际可能的原因：**
- 开发者忘了加 @TableLogic 或 deleted 字段
- 或者觉得收藏表没什么历史价值，直接删更简单

**设计不一致带来的困难：**

1. **数据归档/清理**：
   - 统一清理脚本需要处理两种删除方式
   - 逻辑删除的表需要定期清理已删除数据（如归档到冷存储），物理删除的表不需要
   - 容易遗漏

2. **数据恢复**：
   - 其他表可以通过恢复 deleted = 0 找回数据
   - 收藏表删了就没了，用户误操作不能恢复

3. **审计与追溯**：
   - 逻辑删除的表有数据变更历史
   - 收藏表没有，无法追溯"用户什么时候取消收藏的"

4. **通用代码/框架**：
   - 如果有统一的数据权限过滤、自动填充 deleted 等框架逻辑，收藏表需要特殊处理
   - 增加维护成本

**建议：**
- 除非有明确的性能/空间考量，否则保持一致更好
- 如果真的需要物理删除，应该在设计文档中说明原因

---

## 十、通用框架层（Result + Exception + Context + Config）

### 51. GlobalExceptionHandler.handleException(Exception.class) 中根据 e.getMessage() 包含的关键词判断错误类型——这种"字符串匹配做异常分类"在生产环境的可靠性如何？Spring 的具体异常类型为什么不用？如果 MySQL 驱动版本升级后异常 message 变了，这段逻辑会静默失效吗？

**回答：**

**可靠性如何：**
- **非常不可靠**。这是典型的"脆弱代码"。

**问题：**
1. **异常消息可能变化**：MySQL 驱动、Spring 版本升级后，异常消息的措辞可能改变
2. **国际化问题**：某些异常消息可能随系统语言变化（虽然数据库异常通常是英文的）
3. **误匹配**：如果某个业务异常的 message 刚好包含 "MySQL" 字样，会被误判为数据库连接失败
4. **异常嵌套**：真正的异常可能是 cause，不是 message 本身的字符串
5. **维护成本高**：没人记得这里有字符串匹配，出问题时很难排查

**为什么不用具体异常类型：**
- 可能开发者不熟悉 Spring 的异常体系（DataAccessException 体系）
- 或者想覆盖更多情况（比如 Redis 连接失败可能有多种异常类）

**驱动升级后会静默失效吗：**
- **会**。如果 MySQL 驱动升级后异常 message 变了，字符串匹配不上，就会走默认的"系统繁忙"提示
- 用户看到的是通用错误，运维也不知道具体是数据库还是 Redis 出问题
- 这是静默的功能退化，非常危险

**正确做法：**
```java
// 用具体异常类型捕获
@ExceptionHandler(DataAccessException.class)
public Result<Void> handleDataAccess(DataAccessException e) {
    log.error("数据库访问异常", e);
    return Result.fail(ResultCode.FAIL, "数据库连接失败，请检查 MySQL 是否已启动");
}

@ExceptionHandler(RedisConnectionFailureException.class)
public Result<Void> handleRedis(RedisConnectionFailureException e) {
    log.error("Redis 连接异常", e);
    return Result.fail(ResultCode.FAIL, "Redis 连接失败，请检查 Redis 是否已启动");
}
```
- 精确匹配异常类型，而不是匹配字符串
- 版本升级也不怕，只要异常类名不变

**当前代码的真实目的：**
- 看错误提示的内容（"请检查 MySQL 是否已启动"），这明显是开发/测试环境的友好提示
- 生产环境不应该告诉用户"MySQL 没启动"这种内部信息
- 所以这段代码的定位是"开发环境辅助调试"，不是生产环境错误分类
- 但即使是开发环境，用异常类型也比字符串匹配靠谱

### 52. JacksonConfig 将所有 Long 类型统一序列化为 String，以解决前端 JS 精度丢失问题。但 MyBatis Plus 的 TableId 和分页 total（long 类型）也被序列化为 String，前端分页组件通常期望 total 是数字，这会导致前端兼容性问题吗？

**回答：**

**会导致前端兼容性问题吗：**
- **可能会**，取决于前端分页组件的实现。

**具体分析：**

1. **ID 字段转 String**：
   - 雪花 ID 是 19 位数字，超过 JS Number.MAX_SAFE_INTEGER（2^53 ≈ 9 千万亿，16 位）
   - 不转字符串的话，JS 会丢失精度，ID 显示错误
   - 转 String 是正确的做法

2. **分页 total 转 String**：
   - total 通常不会特别大（比如订单总数最多几百万，远小于 2^53）
   - 转成 String 后，前端分页组件如果用 `total > 100` 这样的比较，字符串比较和数字比较结果不一样
   - 比如 "9" > "10" 在字符串比较中是 true（因为 '9' > '1'），但数字比较是 false
   - 有些前端组件（如 ElementUI 的 Pagination）支持 total 是字符串（会自动转数字），但不是所有都支持

**当前项目的前端：**
- 三个前端都是 Vue + Vite 项目，需要看具体用的什么 UI 组件库
- 如果用了 Element Plus，它的 Pagination 组件 total 属性是 number 类型，传字符串可能有问题

**更好的方案：**

1. **只对 ID 字段转 String**：
   - 不用全局配置，而是在 ID 字段上加 `@JsonSerialize(using = ToStringSerializer.class)`
   - 但每个 ID 都要加，麻烦

2. **自定义序列化器，只转特定范围的 Long**：
   - 比如只对超过 2^53 的 Long 转字符串
   - 实现复杂，且前端收到的类型不统一

3. **全局转 String，前端处理**：
   - 前端统一用 String 类型的 ID，分页 total 前端手动转 number
   - 前后端约定好，也是可行的

4. **用 Jackson 的 MixIn 或自定义注解**：
   - 给需要转字符串的字段加标记，只转这些
   - 最灵活，但配置多

**当前项目的选择（全局转）：**
- 简单粗暴，但可能有分页 total 的兼容性问题
- 前端需要注意把 total 转成数字再用

### 53. MybatisPlusConfig 中的 MetaObjectHandler 自动填充 createdAt 和 updatedAt。但某些实体（如 OrderItem、Cart）没有 updatedAt 字段，strictUpdateFill 在找不到匹配字段时会报错还是静默跳过？你知道 strictInsertFill 和 fillInsert 的差异吗？

**回答：**

**strictUpdateFill 找不到字段时的行为：**
- **静默跳过**。MyBatis Plus 的 MetaObjectHandler 在找不到字段时不会报错
- `strictUpdateFill` 和 `strictInsertFill` 方法（MyBatis Plus 3.3.0+）的"strict"是指：
  - 严格匹配字段类型和名称，不做隐式转换
  - 找不到字段就跳过，不是报错
- 如果是旧版的 `insertFill`，行为类似，也是找不到就跳过

**strictInsertFill 和 fillInsert 的差异：**

| 特性 | fillInsert（旧版） | strictInsertFill（新版） |
|------|-------------------|----------------------|
| 字段匹配 | 宽松，可能有隐式转换 | 严格匹配字段名和类型 |
| 值类型 | 自动转换（如 Integer → Long） | 要求类型完全一致 |
| 字段不存在 | 跳过 | 跳过（都不报错） |
| 推荐程度 | 不推荐（可能有隐式bug） | 推荐 |

**OrderItem 和 Cart 没有 updatedAt 会有问题吗：**
- 不会报错。MetaObjectHandler 会尝试给所有实体填充这些字段，如果实体没有该字段，直接跳过
- 这是合理的设计：不是所有表都需要 updatedAt

**但有一个问题：**
- 逻辑删除（@TableLogic）的 deleted 字段更新时，MyBatis Plus 会不会自动更新 updatedAt？
- 默认配置下，逻辑删除是直接 UPDATE 语句，不走 MetaObjectHandler
- 如果需要逻辑删除时也更新 updatedAt，需要单独配置

### 54. RedisConfig 使用 Jackson2JsonRedisSerializer 并开启了 DefaultTyping.NON_FINAL，这会在 Redis value 中写入 @class 全类名信息。如果实体类（如 MerchantVO）重构包名或修改字段名，Redis 中的旧缓存反序列化会失败吗？当前项目对此有没有任何防御措施？

**回答：**

**重构包名会反序列化失败吗：**
- **会失败**。因为 Redis 值中存了全类名（`@class: "com.takeout.merchant.MerchantVO"`），反序列化时会用这个类名来加载类
- 如果包名变了，类加载失败，抛出 ClassNotFoundException → 反序列化失败 → 异常
- 修改字段名的话，Jackson 默认是忽略未知属性的（FAIL_ON_UNKNOWN_PROPERTIES 默认 false），所以**加字段没问题**，删字段也没问题，但字段类型变了可能出问题

**当前项目的防御措施：**
- **MerchantService.getDetail() 中有防御**：
  ```java
  try {
      Object cached = redisUtil.get(CACHE_KEY + merchantId);
      if (cached instanceof MerchantVO vo) return vo;
  } catch (Exception e) {
      log.warn("商家缓存反序列化失败，清除并从DB加载，merchantId={}", merchantId);
      try { redisUtil.delete(CACHE_KEY + merchantId); } catch (Exception ignored) {}
  }
  ```
  - 捕获异常后清除缓存，从 DB 重新加载
  - 这是"自动修复"的防御措施，很好

- **其他缓存（如菜单缓存）**：
  - DishService.getMenu() 中只有 `@SuppressWarnings("unchecked")`，没有 try-catch
  - 如果菜单缓存反序列化失败，会直接抛出异常，返回 500 给用户
  - **这里没有防御措施！**

**DefaultTyping.NON_FINAL 的安全风险：**
- 更大的问题是安全风险：开启了 DefaultTyping 后，如果攻击者能控制 Redis 中的数据，可以构造恶意的 @class 进行反序列化攻击（RCE）
- 这是 Jackson 的反序列化漏洞（CVE-2017-17485 等）
- 生产环境要谨慎使用 DefaultTyping，特别是 Redis 可能被未授权访问的情况下

**更好的方案：**
1. **不用 DefaultTyping**：指定具体的类型（如 `new Jackson2JsonRedisSerializer<>(MerchantVO.class)`），但这样每个缓存类型需要不同的 RedisTemplate，麻烦
2. **白名单类型**：配置 Jackson 的 PolymorphicTypeValidator，只允许特定包下的类
3. **统一的缓存异常处理**：像 MerchantService 那样，所有缓存读取都加 try-catch，失败则删缓存回源 DB
4. **使用 ProtoBuf 等**：不用 JSON 序列化，换成 Protobuf，性能更好也更安全

### 55. WebMvcConfig 中配置了全局 CORS allowedOriginPatterns("*") 允许所有来源。在接入真实支付网关时，这种配置能过安全审计吗？如果需要收严到指定的前端域名，配置的最小改动路径是什么？

**回答：**

**能过安全审计吗：**
- **不能**。`allowedOriginPatterns("*")` + `allowCredentials(true)` 是非常宽松的配置，存在 CSRF 风险
- 等保测评、安全审计都会要求 CORS 白名单最小化
- 特别是支付相关的接口，CORS 必须严格限制

**等等，allowedOrigins("*") 和 allowCredentials(true) 不能同时设置：**
- 浏览器的安全策略：如果 `Access-Control-Allow-Credentials: true`，则 `Access-Control-Allow-Origin` 不能是 `*`
- 但这里用的是 `allowedOriginPatterns("*")`，Spring 会根据请求的 Origin 动态返回具体的 Origin 值（而不是 *），所以可以和 allowCredentials 同时工作
- 效果上等同于允许所有来源，安全风险一样

**收严到指定域名的最小改动：**

只需要修改 `WebMvcConfig.addCorsMappings()`：

```java
@Override
public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/**")
            .allowedOriginPatterns(
                "https://h5.takeout.com",
                "https://merchant.takeout.com", 
                "https://admin.takeout.com",
                "http://localhost:3001",
                "http://localhost:3002",
                "http://localhost:3003"
            )
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(true)
            .maxAge(3600);
}
```

**更规范的做法：**
- 把允许的域名配置在 `application.yml` 中：
  ```yaml
  cors:
    allowed-origins:
      - https://h5.takeout.com
      - https://merchant.takeout.com
  ```
- 通过 `@ConfigurationProperties` 读取，不同环境（dev/test/prod）配置不同的白名单

**支付回调的特殊处理：**
- 真实支付网关的回调是服务器到服务器的调用，不走浏览器 CORS 策略
- 所以 CORS 配置不影响支付回调
- 支付回调的安全靠验签和 IP 白名单

---

## 十一、数据库与 SQL

### 56. t_order 表的 status 字段注释写着"2=待接单 3=备餐中 5=配送中 6=已完成 7=已取消"，在 Java 常量层面没有任何 OrderStatus 枚举或常量定义，所有状态值散落在 OrderService 各方法中。如果未来要调整状态值（如把"待接单"改成"A"），需要改多少处？如何保证不改漏？

**回答：**

**需要改多少处（估算）：**

后端 Java 代码中：
1. OrderService.submit() — status=1（待支付）、status=2（待接单）
2. OrderService.cancel() — status 1, 2 → 7
3. OrderService.accept() — 2 → 3
4. OrderService.reject() — 2 → 7
5. OrderService.ready() — 3 → 5
6. OrderService.complete() — 5 → 6
7. OrderService.receive() — 5 → 6
8. OrderService.payOrder() — 1 → 2
9. OrderService.listMyOrders() — status 条件
10. OrderService.listMerchantOrders() — status 条件
11. OrderService.listAdminOrders() — status 条件
12. OrderController / OrderMerchantController / OrderAdminController — 可能有状态校验

估算后端：**约 15-20 处**魔法数字

前端代码中：
1. 订单列表状态显示
2. 订单详情状态显示
3. 状态流转按钮显示
4. 订单筛选

三个前端 × 每个约 3-5 处 = **约 10-15 处**

总计：**25-35 处**，非常容易漏改。

**如何保证不改漏：**

1. **定义枚举类**（最基本）：
   ```java
   public enum OrderStatus {
       PENDING_PAYMENT(1, "待支付"),
       PENDING_ACCEPT(2, "待接单"),
       PREPARING(3, "备餐中"),
       DELIVERING(5, "配送中"),
       COMPLETED(6, "已完成"),
       CANCELED(7, "已取消");
   }
   ```
   所有状态值用枚举代替魔法数字

2. **状态机模式**：
   - 定义状态流转图，集中管理状态之间的转换规则
   - 新增状态或调整流转只改状态机配置

3. **前端也定义常量**：
   - 前端定义 orderStatus.js 常量文件
   - 所有状态判断引用常量，不直接写数字

4. **代码审查规则**：
   - 禁止在业务代码中出现魔法数字
   - 新代码必须使用枚举/常量

5. **数据库层面**：
   - 如果状态值要从数字改成字符型（如 "A"），那是更大的改动，需要数据迁移
   - 不建议轻易改状态值的类型

### 57. t_merchant 表的 score 是 DECIMAL(3,1)，意味着最大值 99.9，但对于评分来说 5.0 已经是满分。这个精度设计和字段类型匹配吗？为什么不用 DECIMAL(2,1) 或 TINYINT？

**回答：**

**精度设计匹配吗：**
- **不匹配**。DECIMAL(3,1) 范围是 -99.9 到 99.9，对于 5 分制评分来说太大了
- 合理的应该是 DECIMAL(2,1)（-9.9 到 9.9），足够存 0.0-5.0
- 甚至用 TINYINT 存 0-50（表示 0.0-5.0），显示时除以 10

**为什么用了 DECIMAL(3,1)：**
- 可能是开发者"随手选的"，没有仔细考虑精度
- 或者是从某个模板复制过来的字段定义，没改
- 或者预留了扩展空间（万一以后改成 10 分制、100 分制？）

**不同方案的对比：**

| 方案 | 存储空间 | 精度 | 计算便利性 | 适用场景 |
|------|---------|------|-----------|---------|
| DECIMAL(3,1) | 2 字节 | 1 位小数 | 直接 AVG | 当前方案，精度过剩 |
| DECIMAL(2,1) | 1 字节 | 1 位小数 | 直接 AVG | 推荐，5 分制刚好 |
| TINYINT | 1 字节 | 整数（需除以10） | 计算后要转 | 性能更好，但代码稍麻烦 |
| FLOAT | 4 字节 | 浮点精度 | 直接计算 | 不推荐，有精度误差 |

**实际影响：**
- 没什么实际影响，只是多浪费了一点点存储空间（可忽略）
- 但代码 bug 可能导致存入 10 分、100 分，数据库不报错
- 如果有 CHECK 约束（`CHECK (score >= 0 AND score <= 5)`）更好，但 MySQL 8.0 之前 CHECK 不生效

### 58. t_order 表的 created_at 索引在订单量达到千万级时，按用户查询（WHERE user_id = ? ORDER BY created_at DESC）能有效利用索引吗？idx_user_id 索引需要优化为复合索引 (user_id, created_at) 吗？

**回答：**

**现有 idx_user_id 索引的查询效率：**
- 查询条件是 `WHERE user_id = ? ORDER BY created_at DESC`
- 有 `idx_user_id (user_id)` 索引，MySQL 会：
  1. 先用索引定位 user_id = ? 的所有行（ref 访问）
  2. 再对结果集做 filesort 排序（按 created_at 倒序）
- 如果一个用户的订单不多（比如几十几百单），filesort 很快，没问题
- 如果一个用户订单特别多（比如几万单），filesort 开销会比较大

**需要改成复合索引吗：**
- **推荐改成复合索引 (user_id, created_at)**：
  - 可以同时利用索引做筛选和排序，不需要 filesort
  - 查询性能更稳定，特别是订单量大的用户
  - 复合索引对 `WHERE user_id = ?` 的查询效率和单列索引一样（前缀匹配）

**但还要看其他查询：**
- 商家端查询：`WHERE merchant_id = ? ORDER BY created_at DESC` —— 同样需要 `(merchant_id, created_at)` 复合索引
- 管理员查询：`WHERE status = ? ORDER BY created_at DESC` —— 需要 `(status, created_at)`

**索引数量的权衡：**
- 索引越多，写入越慢（INSERT/UPDATE 时要更新所有索引）
- 订单表是写多读多的表，索引不宜过多
- 但 (user_id, created_at) 和 (merchant_id, created_at) 是核心查询，值得加

**千万级数据下的建议：**
1. 建立复合索引 `idx_user_created (user_id, created_at DESC)`
2. 建立复合索引 `idx_merchant_created (merchant_id, created_at DESC)`
3. 单列的 idx_user_id、idx_merchant_id 可以删掉（复合索引前缀能替代）
4. 考虑按时间分表（如按月分表），单表数据控制在几百万

### 59. init.sql 中通过 ALTER TABLE ADD COLUMN IF NOT EXISTS 的模拟来新增 discount 和 user_coupon_id 字段——为什么在建表时就直接包含这些字段，要拆到后面通过迁移脚本加？如果增量迁移脚本多于一个，怎么保证迁移顺序的幂等性？

**回答：**

**为什么拆到后面加字段：**

推测原因：
1. **历史演进**：早期版本没有优惠券功能，后来加上的，所以是 ALTER TABLE 加字段
2. **增量迁移脚本演示**：演示如何做幂等的数据库迁移
3. **模拟真实项目**：真实项目中表结构会随版本迭代变化，不是一次设计好的

**这种方式的问题：**
1. **init.sql 既是建表脚本又是迁移脚本**，职责不清晰
2. **多人协作时容易冲突**：两个人同时加不同字段，都写在 init.sql 最后，合并冲突
3. **没有版本追踪**：不知道哪些字段是哪个版本加的
4. **生产环境不能直接跑 init.sql**：生产数据库已经存在，需要单独的迁移脚本

**多个迁移脚本如何保证顺序和幂等性：**

当前方式（全部写在 init.sql 里）：
- 按 SQL 语句顺序执行
- 每个 ALTER TABLE 前都有 `information_schema.COLUMNS` 检查，保证幂等（已存在则跳过）
- 但顺序靠人工维护，容易出错

**更规范的方案：引入 Flyway/Liquibase**
1. **Flyway**：
   - 每个迁移脚本命名 `V1.1__add_discount_column.sql`、`V1.2__add_coupon_id.sql`
   - 按版本号顺序执行
   - 每个脚本只执行一次，执行记录存在 flyway_schema_history 表
   - 幂等性由框架保证（已执行的脚本不会再执行）

2. **Liquibase**：
   - 用 XML/YAML/JSON 定义变更，支持更复杂的变更
   - 有 rollback 支持
   - 也是按顺序执行，记录变更历史

**当前项目的适用建议：**
- 项目初期、表结构变化快：可以接受 init.sql 的方式
- 项目稳定、多人协作：建议引入 Flyway，轻量且成熟

### 60. t_coupon 和 t_user_coupon 是一对多的关系，但 t_user_coupon 中冗余存储了 title、min_order_price、discount、valid_end 四个字段。这种快照式设计解决了什么业务问题？如果不做快照，优惠券模板修改后会影响所有已领券的用户，这是预期行为还是 Bug？

**回答：**

**快照式设计解决的业务问题：**

1. **商家修改优惠券模板不影响已领取的券**：
   - 比如商家发了满 50 减 10 的券，用户领了之后，商家把券改成满 100 减 10
   - 如果是直接引用模板，用户已领的券也会变成满 100 才能用，用户体验极差
   - 快照式设计保证了"用户领到的是什么样就是什么样"

2. **优惠券模板可以删除**：
   - 商家删除优惠券模板后，用户已领的券仍然可用
   - 如果没有快照，模板删了用户券就没法用了

3. **性能考虑**：
   - 查询用户优惠券不需要 JOIN 模板表，单表查询更快
   - 虽然冗余了几个字段，但省了 JOIN

4. **valid_end 的独立性**：
   - 每张用户券的过期时间可能不一样（比如"领取后 7 天有效"），不是模板的 valid_end 直接复制
   - 但当前代码是直接复制模板的 validEnd，说明是"固定过期时间"模式

**这是预期行为还是 Bug：**
- **预期行为**。这是电商优惠券系统的标准设计
- 用户领取优惠券后，这张券的属性就固定了，商家后续修改模板不影响已发放的券
- 这叫"优惠券快照"或"用户券实例化"

**但也有不同的设计选择：**
- 有些业务场景希望"模板修改后所有券跟着变"（比如运营发现写错了价格，要紧急修正）
- 这种情况下就不应该做快照，而是直接关联模板
- 但外卖场景通常是快照模式，因为涉及用户资产，不能随便改

**扩展：更复杂的设计**
- 模板和用户券可以混合：大部分字段快照，某些字段（如使用规则说明）可以实时从模板取
- 或者增加"同步更新"开关，商家选择是否同步更新已发放的券

---

## 十二、缓存策略

### 61. 项目使用了三种不同的 Redis 缓存模式：(a) 验证码等短 TTL 数据、(b) 商家详情等长 TTL 全量缓存（更新时主动淘汰）、(c) 菜品库存的常态化旁路缓存（更新时同步 Redis + MySQL）。这三种模式分别选型的依据是什么？

**回答：**

**三种模式的选型依据：**

**(a) 验证码等短 TTL 数据**
- **特征**：写一次，读一次/几次，有明确有效期
- **选型依据**：
  - 数据是临时的，过期自动失效，不需要主动删除
  - Redis 天然适合存短生命周期的 KV 数据
  - 一致性要求：不需要和数据库同步，Redis 就是唯一数据源
  - 例子：短信验证码、refreshToken、分布式锁

**(b) 商家详情等长 TTL 全量缓存（Cache Aside 模式，更新时删缓存）**
- **特征**：读多写少，数据在 MySQL 有权威存储，Redis 是缓存
- **选型依据**：
  - 读多写少，缓存命中率高
  - 更新时主动淘汰缓存（失效缓存），下次读取时回源
  - 一致性：最终一致，有短暂的时间窗口（缓存删除到下次回源之间）
  - 为什么不更新缓存而是删除：防止并发更新导致缓存数据错乱（先更新 DB 的人后更新缓存）
  - 例子：商家详情、菜单列表

**(c) 菜品库存的常态化旁路缓存（Read/Write Through 风格）**
- **特征**：读写都很频繁，对性能和一致性要求高
- **选型依据**：
  - 库存扣减是高频操作（下单高峰），每次都查数据库扛不住
  - Redis 作为主要读写通道，MySQL 作为持久化备份
  - 写操作同时更新 Redis 和 MySQL（双写）
  - 一致性：Redis 是准实时的，MySQL 同步更新，两边一致
  - 为什么用 Lua：保证"检查+扣减"的原子性，防止超卖
  - 例子：菜品库存

**选型的核心考量因素：**
1. **读写比**：读多写少用 Cache Aside，读写都多用 Write Through
2. **一致性要求**：强一致性需求不能用缓存，或用分布式锁保证
3. **数据权威性**：Redis 是唯一数据源（如验证码）还是缓存（如商家信息）
4. **过期策略**：自然过期（验证码）vs 主动失效（商家信息）vs 不过期（库存）
5. **原子性要求**：简单 KV 用 INCR/DECR，复杂操作用 Lua

### 62. DishService 中的菜品库存既有 Redis 中的实时库存，又有 MySQL 中的持久化库存——两者之间通过 Lua 脚本扣减 Redis、再同步扣减 MySQL。如果在 Redis 扣减成功后、MySQL 同步前 JVM 宕机，重启后 Redis 库存数据丢失（未配置 RDB/AOF），库存会发生什么？这种数据不一致有补偿机制吗？

**回答：**

**宕机后的库存情况：**

场景：Redis 扣减成功 → MySQL 扣减前 JVM 宕机

1. **Redis 数据丢失（无持久化）**：
   - 重启后 Redis 库存为空（或上次 RDB 快照的数据）
   - MySQL 中库存没变（因为还没扣减）
   - 下次下单时，Redis key 不存在，触发 syncStockToRedis，从 MySQL 同步库存到 Redis
   - MySQL 的库存是准确的（因为那次宕机的扣减没成功）
   - 结果：**库存最终以 MySQL 为准，没有超卖**，但 Redis 显示的库存比实际多（因为那次"应该扣的"没扣成）

等等，再仔细分析：
- Redis 扣了（比如 100→99），MySQL 没扣（还是 100）
- 宕机重启，Redis 没持久化，库存 key 丢失
- 下次下单，发现 key 不存在，从 MySQL 同步（100）
- 结果：库存从 100 重新开始，之前那次扣减"丢失"了
- 但那次扣减对应的订单呢？因为 submit() 是一个大事务，MySQL 扣减在事务里，如果 MySQL 还没扣就宕机了，事务也没提交，订单也没创建
- 所以**业务上是正确的**：没有订单，库存也没少

**但如果 Redis 有持久化（AOF/RDB）：**
- 重启后 Redis 库存是 99（扣减后的值）
- MySQL 库存是 100（没扣成）
- Redis 比 MySQL 少 1
- 结果：**少卖了 1 件**（Redis 库存少，用户看到库存不足，但实际 MySQL 还有）
- 这种情况不会超卖，只是库存少了，属于"安全的不一致"

**补偿机制：**
- 当前代码中，`checkAndDeduct()` 的 fallback 路径（key miss 时从 MySQL 同步）就是一种补偿
- 但 Redis 有持久化的情况下，key 不会 miss，所以补偿不了
- 真正的补偿机制应该是：**定时校准**——定时任务把 MySQL 库存同步到 Redis，修正不一致
- 或者：**以 MySQL 为准**——Redis 只是缓存，高并发下可能不准，定期以 MySQL 为准校准

**当前项目的风险：**
- 如果 Redis 没开持久化（默认配置），宕机后库存数据全丢，需要从 MySQL 全量同步
- 同步期间大量请求回源 DB，可能压垮数据库（缓存雪崩）
- 建议：开启 Redis AOF 持久化，保证重启后数据不丢

### 63. MerchantService.getDetail() 的缓存反序列化异常处理（catch Exception + 清除缓存 + 回源 DB）能解决类变更导致的兼容性问题，但如果 Redis 中存储的是旧版本的 MerchantVO（缺少新字段），反序列化出来的对象会少字段吗？Jackson 对这个的默认行为是什么？

**回答：**

**旧版本 MerchantVO（缺少新字段）的反序列化：**
- Jackson 默认配置下，**缺少的字段会被设为默认值**（null、0、false 等）
- 不会抛异常，因为 `DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES` 默认是 false（忽略未知属性）
- 等等，这里说的是"旧缓存缺少新字段"，不是"多了未知字段"：
  - 旧缓存 → 新类：缓存的 JSON 里没有新字段，Jackson 反序列化时新字段就是 null（或默认值）
  - 新缓存 → 旧类：JSON 里有类中没有的字段，Jackson 默认忽略，不报错

**所以"少字段"的情况不会触发 catch，反序列化会成功但新字段为 null：**
- 比如 MerchantVO 新增了 `businessLicense` 字段
- Redis 中存的是旧版本的缓存（没有 businessLicense）
- 反序列化出来的 MerchantVO 对象中，businessLicense = null
- 不会抛异常，所以不会触发"清除缓存回源 DB"的逻辑
- 结果：商家详情页 businessLicense 字段一直显示为空，直到缓存过期

**这是一个隐蔽的问题！**

**如何解决：**

1. **缓存版本号**：
   - 在缓存 key 中加版本号，如 `merchant:info:v2:{id}`
   - 类结构变更时，版本号 +1，旧缓存自然失效
   - 需要运维成本，但最可靠

2. **给缓存对象加 serialVersionUID/版本字段**：
   - 在 VO 中加 version 字段，反序列化后校验版本
   - 版本不一致则删缓存回源

3. **缓存时间调短**：
   - 从 30 分钟改成 5 分钟，降低不一致的时间窗口
   - 简单但牺牲了缓存命中率

4. **主动预热**：
   - 发布新版本后，主动预热新缓存（遍历商家 ID 刷一遍）
   - 运维成本高

**当前代码的 catch 能解决什么：**
- 只能解决"类名变更"、"字段类型变更"等会导致反序列化异常的情况
- 解决不了"新增字段"这种兼容性变更（因为 Jackson 能正常反序列化，只是字段为默认值）

### 64. redisUtil.set(MENU_CACHE_PREFIX + merchantId, menu, 10, TimeUnit.MINUTES) 使用 10 分钟固定过期时间。如果大量商家的菜单都在同一时间点缓存失效，大量请求同时回源 DB 会导致缓存雪崩吗？为什么没有加随机偏移？

**回答：**

**会导致缓存雪崩吗：**
- **理论上会**。如果所有商家的菜单都是在同一时间点（比如系统启动后 1 分钟内）被首次访问并缓存，那么 10 分钟后这些缓存会同时失效
- 同时失效瞬间，大量请求回源数据库，可能把数据库打垮（缓存雪崩）
- 但实际上，不同商家的菜单首次访问时间是分散的（用户访问时间随机），所以过期时间也自然分散
- 除非有批量预热缓存的操作（比如启动时一次性把所有商家菜单都缓存了），否则不太容易出现同时失效

**为什么没有加随机偏移：**
- 可能开发者没考虑到缓存雪崩问题
- 或者项目初期商家数量少（几十几百家），同时回源也压不垮数据库
- 或者认为"更新时主动删缓存"策略下，缓存很少活够 10 分钟（商家改菜品就会删缓存）

**缓存雪崩的防护措施：**

1. **过期时间加随机偏移**：
   - `TLL = 10分钟 + random(0, 5分钟)`
   - 让过期时间分散开，避免同时失效
   - 实现简单，效果好

2. **互斥锁/单线程回源**：
   - 缓存失效时，只有一个线程去查数据库并更新缓存，其他线程等待
   - 防止同一 key 的大量并发请求同时回源（缓存击穿）
   - 可以用 Redis 分布式锁实现

3. **缓存永不过期 + 异步更新**：
   - 缓存不设 TTL，物理上不过期
   - 后台定时任务异步更新缓存
   - 或者更新数据库时同步更新缓存（不是删除）

4. **多级缓存**：
   - Caffeine 本地缓存 + Redis 分布式缓存
   - 即使 Redis 挂了，本地缓存还能顶一阵

**当前项目的风险等级：**
- 商家数量不多（几十到几百），即使全部同时回源，数据库也能扛住
- 菜单查询不算重（一次 JOIN 查询）
- 所以风险较低，但加随机偏移是最佳实践，应该加上

---

## 十三、并发与事务

### 65. OrderService.submit() 中扣库存（Redis Lua）和写订单（MySQL @Transactional）是跨资源的事务，没有使用分布式事务（XA、TCC、Saga）。如果扣库存成功但 MySQL 写入失败，代码里有什么补偿措施？业务上能容忍库存"虚扣"吗？

**回答：**

**MySQL 写入失败时的情况：**
- Redis 扣减成功 → MySQL 扣减成功（在同一个 Java 方法内，checkAndDeduct 里就扣了 MySQL）→ 然后写订单 → 写订单失败 → 事务回滚
- 等等，看代码顺序：
  ```
  checkAndDeduct() {
      Lua 扣 Redis 库存
      MySQL 扣库存（通过 setSql）
  }
  → 写订单（orderMapper.insert）
  → ...
  ```
- checkAndDeduct 内部已经做了 Redis 扣减 + MySQL 扣减
- 如果后面写订单失败，`@Transactional` 回滚：
  - MySQL 库存扣减回滚 ✓
  - Redis 库存扣减**不回滚** ✗

**补偿措施：**
- 当前代码中**没有明确的补偿措施**！
- 也就是说，如果写订单失败，Redis 库存会比 MySQL 少（因为 Redis 扣了没回滚，MySQL 回滚了）
- 这是"虚扣"——库存被扣了，但没有对应的订单

**业务上能容忍吗：**
- 这种情况发生的概率不高（写订单失败通常是字段问题或数据库宕机）
- 虚扣的结果是 Redis 库存比实际少，用户看到的库存比实际少，不会超卖，只是少卖
- 对商家的影响：少卖几件，损失不大
- 对用户的影响：可能看到"库存不足"但实际还有货

**但如果持续发生，问题会累积：**
- 每次下单失败都虚扣一点，时间长了 Redis 库存和 MySQL 库存差距越来越大
- 最终菜品显示"售罄"但实际还有很多库存

**补偿方案：**

1. **catch 中回滚 Redis 库存**：
   - submit() 方法 catch 异常，手动调用 revertStock 回滚 Redis 库存
   - 简单直接，但要注意回滚操作本身也可能失败

2. **定时校准**：
   - 定时任务（如每天凌晨）把 MySQL 库存同步到 Redis
   - 修正不一致

3. **事务消息**：
   - 用 RocketMQ 事务消息，保证"扣库存"和"创建订单"的最终一致性
   - 架构复杂度高

4. **先写订单再扣库存**：
   - 先创建订单（待支付），支付成功后才扣库存
   - 但这样库存就不能提前锁定了，可能下单后支付时发现没货了

### 66. 订单取消和订单支付同时调用时，cancel() 和 payOrder() 都通过 Redis 分布式锁 + @Transactional 保护。如果取消先获取到锁，将状态从 1→7，支付线程等待锁，获取锁后 CAS 更新 WHERE status = 1 会返回 0 行影响——这个逻辑是正确的吗？支付线程的 updateStatusWithLock 中 orderMapper.update 返回 0 后抛异常，事务回滚，但 Redis 锁已经释放了，会产生什么影响？

**回答：**

**逻辑是正确的吗：**
- **业务逻辑是正确的**。并发下，要么取消成功要么支付成功，不会出现"既取消了又支付了"的情况
- CAS 更新（WHERE status = expected）保证了只有状态符合预期才能更新
- 这是乐观锁的思想，虽然用了分布式锁，但数据库层面又加了一层保障

**支付线程抛异常的影响：**
- 支付线程拿到锁后，执行 `updateStatusWithLock(order, 1, 2, null)`
- 因为订单已经被取消（status=7），UPDATE 返回 0 行
- 代码抛出 BusinessException("订单状态已变更，操作失败")
- 事务回滚（但其实也没做什么更新，回滚不回滚无所谓）
- finally 中释放锁

**锁已经释放了会有什么影响：**
- 没有什么不良影响。锁本来就是要释放的
- 异常是正常的业务冲突（并发下的预期失败），不是 bug
- 问题在于：
  1. **用户体验**：用户点支付，提示"订单状态已变更"，用户困惑（订单去哪了？）
  2. **异常控制流**：用异常处理正常的业务并发冲突，不太优雅

**更好的处理方式：**
- 返回 0 行不一定要抛异常，可以返回"操作失败，订单状态已变更"的友好提示
- 前端收到提示后自动刷新订单状态，用户看到"订单已取消"就明白了

**锁和事务的时序问题（之前讨论过）：**
- 更隐蔽的问题是"锁释放 → 事务提交"之间的窗口
- 但在这个场景下，因为有 CAS 更新兜底，数据是安全的
- 只是性能和体验问题

**总结：**
- 数据一致性 ✓（正确）
- 并发安全性 ✓（有锁 + CAS 双重保障）
- 用户体验 ✗（异常提示不友好）
- 代码优雅性 ✗（用异常处理正常业务场景）

### 67. 多个 @Transactional 方法之间存在嵌套调用（如 DishService.add() → 内部调用 dishMapper.insert() + dishSpecMapper.insert()），外层如果又有 @Transactional（如 OrderService.submit() 是 @Transactional，调用了 dishService.checkAndDeduct() 也是 @Transactional），Spring 默认的事务传播行为是什么？内层事务失败会导致外层全部回滚吗？

**回答：**

**Spring 默认事务传播行为：**
- 默认是 `Propagation.REQUIRED`
- 含义：如果当前存在事务，则加入该事务；如果当前没有事务，则创建一个新事务

**嵌套调用的情况：**

场景 1：外层有事务，内层方法也有 @Transactional（REQUIRED）
- 内层方法加入外层的事务，成为同一个事务
- 内层方法异常回滚 → 整个事务回滚（外层也回滚）
- 外层方法异常回滚 → 整个事务回滚（内层也回滚）
- 因为它们是同一个事务

场景 2：内层方法用 try-catch 吞了异常
- 内层异常被 catch 了，没有抛出
- 外层事务感知不到异常，不会回滚
- 但内层自己的操作呢？因为是同一个事务，内层的数据库操作已经写了，但因为是同一个事务上下文，catch 住的话不会标记 rollback
- 等等，具体要看异常类型：
  - 如果是 RuntimeException，Spring 默认会标记回滚
  - 但如果 catch 住了不抛出，Spring 不知道有异常，就不会回滚
  - 结果：内层的操作被提交了（因为外层正常提交）

**当前代码中的情况：**
- `OrderService.submit()` 有 `@Transactional(rollbackFor = Exception.class)`
- `DishService.checkAndDeduct()` 也有 `@Transactional(rollbackFor = Exception.class)`
- 默认 REQUIRED 传播，所以 checkAndDeduct 加入 submit 的事务
- checkAndDeduct 中如果抛出异常，会导致整个 submit 事务回滚 ✓
- 这是符合预期的

**rollbackFor = Exception.class 的含义：**
- Spring 默认只对 RuntimeException 和 Error 回滚
- 加上 rollbackFor = Exception.class 后，对所有 Exception（包括 checked exception）都回滚
- 更安全，防止 checked exception 不回滚

**其他传播行为（了解）：**
- REQUIRES_NEW：内层方法新建一个独立事务，外层事务挂起
- NESTED：嵌套事务（保存点），内层回滚不影响外层，外层回滚会连带内层
- SUPPORTS / NOT_SUPPORTED / MANDATORY / NEVER

### 68. OrderService.receive() 将状态从 5→6，但 OrderMerchantController.complete() 也可以将状态从 5→6，两个操作都没有角色互斥校验——如果商家点击"完成配送"的同时用户点击"确认收货"，两个线程同时执行 updateStatusWithLock(order, 5, 6, null)，在分布式锁的串行化下第二次调用会因为 WHERE status = 5 找不到而失败。这算不算幂等设计？用户在"确认收货"看到失败提示会困惑吗？

**回答：**

**这算不算幂等设计：**
- **算一种幂等**。因为两个操作做的是同一件事（把订单从 5 改成 6），不管谁先执行，最终结果都是 status=6
- 第二次执行失败（0 行影响），但数据状态是正确的
- 幂等的定义：多次执行和一次执行的效果相同。这里虽然第二次抛异常，但最终数据状态是一致的，所以是数据层面的幂等，不是接口层面的幂等

**但接口层面不算幂等：**
- 幂等接口应该是：调用一次和调用 N 次，返回结果也应该一样（都是成功）
- 这里第二次调用返回"操作失败"，从接口语义上不是幂等的

**用户体验问题：**
- 用户点"确认收货"，结果提示"订单状态已变更，操作失败"
- 用户会困惑："什么意思？到底成功了还是失败了？"
- 好一点的提示应该是："订单已完成"（即使你不操作，它也已经完成了）

**改进建议：**

1. **接口层面做幂等：**
   - 如果 UPDATE 返回 0 行，查询当前状态
   - 如果已经是目标状态，直接返回成功（幂等成功）
   - 如果不是目标状态，再抛出"状态不匹配"异常

2. **区分角色的操作记录：**
   - 虽然都是 5→6，但可以记录是谁操作的（商家完成配送 vs 用户确认收货）
   - 可以加字段如 `delivery_complete_time`、`receive_time`，分别记录两个时间点
   - 更完整的订单轨迹

3. **前端优化：**
   - 前端收到"状态已变更"的错误后，自动查询最新订单状态
   - 如果已经是完成状态，显示"订单已完成"
   - 不让用户看到错误提示

**为什么两个端点都能改：**
- 这是业务设计：配送完成可以由商家确认（商家点"已送达"），也可以由用户确认（用户点"确认收货"）
- 谁先确认谁生效，另一个自动完成
- 合理的业务设计，只是实现上可以更友好

---

## 十四、API 设计

### 69. 项目使用 Knife4j（OpenAPI 3）作为 API 文档方案，但在多个接口中（如 DishController.list() 的 6 个 @RequestParam），没有使用 @Parameter 注解做详细说明。Knife4j 生成的文档对前端可读性如何？

**回答：**

**当前文档的可读性：**
- **中等偏差**。有接口列表、有参数名、有返回结构（如果有 DTO），但缺少详细说明

**具体问题：**

1. **参数说明缺失**：
   - 比如 `DishController.list()` 有 6 个 @RequestParam，但没有说明每个参数的含义、取值范围、是否必填
   - 前端只能靠参数名猜（比如 status 是什么？0 和 1 分别代表什么？）
   - 分页参数 page/size 还好猜，status、categoryId 这种就容易误解

2. **返回值说明缺失**：
   - 返回 `Result<List<DishVO>>`，VO 中的字段如果没有 `@Schema` 注解，也没有说明
   - 前端不知道每个字段是什么意思、什么格式

3. **接口描述缺失**：
   - 没有 `@Operation(summary = "xxx", description = "xxx")`
   - 前端不知道接口是干嘛的、有什么注意事项

**Knife4j 的价值：**
- 即使注解不全，Knife4j 仍然有价值：
  1. 自动生成接口列表，知道有哪些接口
  2. 能看到请求方法（GET/POST）、路径
  3. 能在线调试（发送请求看响应）
- 但要达到"前端照着文档就能对接"的程度，还差很多

**最佳实践：**
- 所有接口加 `@Operation(summary = "接口名", description = "详细说明")`
- 所有参数加 `@Parameter(description = "说明", required = true, example = "1")`
- 所有 DTO/VO 字段加 `@Schema(description = "说明", example = "xxx")`
- 枚举类型用 `@Schema(implementation = xxxEnum.class)`

**为什么没写：**
- 项目快速迭代，开发时间紧，文档滞后
- 没有强制的代码规范
- 前后端可能是同一个人或沟通频繁，文档需求不迫切

### 70. MerchantAdminController 和 UserAdminController 中 requireAdmin() 使用 UserContext.getUserRole() != UserRole.ADMIN 判断权限，这是一种硬编码的粗粒度权限校验。如果未来要引入"超级管理员"和"运营管理员"两种角色（运营管理员只能审核商家、不能管理用户），现有的权限模型需要怎么改造？

**回答：**

**现有模型的问题：**
- 只有角色判断，没有权限概念
- 每个 Controller 里手动写 requireAdmin()，散落在各处
- 角色和权限绑定死了（写死在代码里），加新角色要改很多地方

**改造方向：RBAC（基于角色的访问控制）**

**步骤一：引入权限常量/枚举**
```java
public enum Permission {
    USER_MANAGE("user:manage", "用户管理"),
    MERCHANT_AUDIT("merchant:audit", "商家审核"),
    MERCHANT_MANAGE("merchant:manage", "商家管理"),
    ORDER_MANAGE("order:manage", "订单管理"),
    // ...
}
```

**步骤二：角色-权限关联**
- 数据库增加 `t_role`、`t_permission`、`t_role_permission` 表
- 或者简化：在 UserRole 枚举中定义每个角色拥有的权限

**步骤三：统一权限校验**
- 方案 A：注解方式（推荐）
  ```java
  @PreAuthorize("hasPermission('merchant:audit')")
  @PostMapping("/audit")
  public void audit(...) { ... }
  ```
  配合 Spring Security 或自定义 AOP

- 方案 B：工具类方式
  ```java
  PermissionUtil.checkPermission(Permission.MERCHANT_AUDIT);
  ```
  比硬编码角色好，但还是要手动调用

**步骤四：改造现有角色**
- SUPER_ADMIN（超级管理员）：拥有所有权限
- OPERATOR_ADMIN（运营管理员）：只有商家审核、订单管理权限
- ADMIN（普通管理员）：保留原有，或重新定义

**改造工作量估算：**
- 权限注解/AOP 框架：1-2 天
- 所有接口加权限注解：每个 Controller 改几行，约 20-30 个接口
- 角色-权限配置：数据库表 + 初始化数据
- 前端菜单/按钮权限控制：也要相应改造

**当前代码的快速扩展方案（不引入框架）：**
- 在 UserRole 枚举里加权限集合：
  ```java
  public enum UserRole {
      ADMIN(Set.of("user:manage", "merchant:manage", "order:manage")),
      OPERATOR(Set.of("merchant:audit", "order:manage"));
      
      private final Set<String> permissions;
  }
  ```
- 自定义注解 + AOP 做权限校验
- 轻量改造，不需要引入 Spring Security

### 71. 几乎所有 Controller 的返回值都统一为 Result<T>，但 OrderAdminController 缺少 @Tag 和 @Operation 注解（与其他 Controller 不一致），Knife4j 页面上这个接口会缺少描述。这种不一致是怎么产生的？

**回答：**

**不一致产生的原因：**

1. **不同时间开发**：
   - 可能 OrderAdminController 是最后加的，开发时赶时间忘了写注解
   - 或者是不同的人开发的，没有参考已有规范

2. **没有代码规范/Review 机制**：
   - 团队没有约定"所有 Controller 必须有 @Tag 和 @Operation"
   - 代码审查时也不检查文档注解

3. **管理后台接口优先级低**：
   - 管理后台接口少、使用频率低，文档质量容易被忽略
   - 前端和后端可能是同一个人，不需要靠文档对接

4. **渐进式开发**：
   - 先写功能，文档后面补——然后就忘了补

**这种不一致的影响：**
1. **前端对接效率低**：新接手的前端不知道管理后台接口怎么用
2. **新人上手慢**：新加入的后端开发者看到"有人写有人不写"，倾向于也不写（破窗效应）
3. **文档质量下降**：久而久之，越来越多接口不加注解，文档就废了
4. **维护成本高**：接口变更时，有注解的还要同步更注解，没注解的反而"省事"，加剧劣化

**如何避免：**
1. **代码规范**：明确要求所有对外接口必须有完整的 OpenAPI 注解
2. **代码审查 CheckList**：把 API 文档注解纳入 CR 检查项
3. **静态代码检查**：用 Checkstyle/PMD 自定义规则检查 Controller 是否有 @Tag/@Operation
4. **统一基类/模板**：新 Controller 生成时有模板，自带注解框架
5. **定期检查**：项目里程碑时检查文档覆盖率

### 72. MerchantController.updateMy() 的 status 参数是 0=休息中 1=营业中，但 Merchant 表的 status 字段具体是 0=审核中 1=营业中 2=打烊 3=封禁 4=审核拒绝。两个 status 含义不统一，Controller 层的"营业状态"和实体层的"商家状态"是同一个字段但含义不同段位的值，这种映射关系会给后续维护带来什么困扰？

**回答：**

**含义差异的具体分析：**

Merchant 表 status（商家状态/审核状态）：
- 0 = 审核中
- 1 = 营业中
- 2 = 打烊
- 3 = 封禁
- 4 = 审核拒绝

updateMy 的 status（营业状态）：
- 0 = 休息中（对应表中的 2=打烊？）
- 1 = 营业中（对应表中的 1=营业中）

**等等，看代码实际怎么映射的：**
- MerchantService.updateStatus() 中：
  - 允许传入的 status 是 1 或 2（营业中或打烊）
  - `request.status() != 1 && request.status() != 2` 会抛参数错误
  - 所以 Controller 层的 status 0/1 到 Service 层是怎么转的？需要看 MerchantController

如果 Controller 层的 status 是：
- 0 → 打烊（status=2）
- 1 → 营业中（status=1）

那就是典型的"接口参数和数据库字段值不一致"。

**维护困扰：**

1. **心智负担重**：开发者需要记住"这个 status 是接口的还是数据库的"，不同语境下含义不同
2. **容易搞混**：写代码时把接口值直接存到数据库，或者反过来，导致 bug
3. **排查困难**：查日志看到 status=0，要先搞清楚是哪个层面的 0
4. **文档歧义**：API 文档写 status=0/1，新人看数据库以为 status=0 是审核中，困惑
5. **扩展困难**：要加新的营业状态（如"暂停营业"），需要在两个层面都加映射，容易漏

**更好的设计：**

1. **拆分字段**：
   - `audit_status`：审核状态（0审核中 1通过 2拒绝 3封禁）
   - `business_status`：营业状态（0休息 1营业中 2打烊 3暂停）
   - 两个维度分开，互不干扰

2. **枚举类型**：
   - 接口参数用枚举（如 `BusinessStatus.OPEN` / `BusinessStatus.CLOSED`）
   - 数据库也用枚举或字符串，一目了然

3. **统一命名**：
   - 不同含义的字段不要用相同的名字
   - 比如接口里叫 `businessStatus`，不要叫 `status`

---

## 十五、跨模块设计问题

### 73. OrderService 依赖了 DishService、CouponService、MerchantService、UserAddressService、CartService 五个 Service——这是 Service 层的"大管家"模式。如果每个 Module 都这样互相依赖，最终会形成怎样的依赖图？如果未来要拆分微服务，这种图怎么解耦？

**回答：**

**依赖图会变成什么样：**
- 目前是 OrderService 依赖其他 5 个，是中心辐射状的（Order 是中心）
- 如果每个模块都这样，会形成**网状依赖**：A→B、B→C、C→A、A→D 等等
- 最终会演变成"大泥球"（Big Ball of Mud）——任何改动都可能影响其他模块
- 循环依赖的风险极高（比如 CouponService 也依赖 OrderService 查询订单，就循环了）

**当前的依赖方向：**
- OrderService → DishService（依赖）
- OrderService → CouponService（依赖）
- OrderService → MerchantService（依赖）
- OrderService → UserAddressService（依赖）
- OrderService → CartService（依赖）
- ReviewService → OrderService（依赖）
- ReviewService → MerchantService（依赖）
- DishService → MerchantService（依赖）

目前还没有明显的循环依赖，但 OrderService 是依赖集中点。

**拆微服务时怎么解耦：**

1. **识别领域边界**（DDD 限界上下文）：
   - 用户域（用户、地址）
   - 商家域（商家、菜品、分类）
   - 订单域（订单、购物车、支付）
   - 营销域（优惠券、活动）
   - 评价域（评价、评分）

2. **引入防腐层（ACL）**：
   - 每个域通过防腐层访问其他域，不直接依赖对方的 Service
   - 防腐层中做数据转换、接口适配
   - 未来拆微服务时，把防腐层的实现从本地调用改成 RPC 调用即可

3. **领域事件（Domain Event）**：
   - 把同步调用改成异步事件驱动
   - 比如"订单支付成功"事件，优惠券服务监听事件核销优惠券，而不是订单服务直接调用优惠券服务
   - 解耦效果最好，但架构复杂度高

4. **门面模式（Facade）**：
   - 每个模块提供统一的门面接口，内部细节不对外暴露
   - 依赖门面而不是具体 Service

5. **先拆数据后拆服务**：
   - 先把数据库表按领域拆到不同 schema，禁止跨库 JOIN
   - 逼自己用接口调用而不是直接查表

**当前代码的可拆解性：**
- OrderService 依赖太多，是最难拆的部分
- 建议先从简单的模块开始拆（如评价、收藏），积累经验

### 74. 优惠使用的校验（CouponService.validateAndGetDiscount()）在 OrderService.submit() 中调用，但校验通过后并没有锁定该优惠券，只是在订单提交成功后才调用 couponService.markUsed()。如果用户 A 同时提交两个订单（使用同一张优惠券），两个请求同时进行校验都通过，但只有一个能 markUsed 成功，另一个会报错。用户体验上，用户支付失败后才知道优惠券被用了——这个时序问题怎么在前端规避？

**回答：**

**问题描述准确，这是经典的"校验与操作分离"的并发问题。**

**当前时序：**
```
请求A: 校验通过 → 扣库存 → 写订单 → markUsed 成功
请求B: 校验通过 → 扣库存 → 写订单 → markUsed 失败 → 事务回滚
```
请求 B 会回滚，但用户已经进入支付流程了，体验不好。

**后端解决方案：**

1. **预占/锁定优惠券**：
   - 校验通过后立即把优惠券状态改成"预占中"（如 status=2）
   - 订单提交成功后改成"已使用"（status=1）
   - 订单失败/超时后自动释放（定时任务扫描 + 释放）
   - 类似库存预占的思路
   - 优点：用户在订单确认页就知道优惠券能不能用
   - 缺点：状态机复杂，需要定时任务释放

2. **分布式锁 + 串行化**：
   - 提交订单时按 userCouponId 加分布式锁
   - 同一优惠券的订单提交串行化
   - 第一个成功，第二个在校验阶段就阻塞
   - 但锁的粒度细，对性能影响不大
   - 缺点：还是要等第一个提交完才知道第二个失败，体验也一般

3. **乐观锁 + 提前 markUsed**：
   - 校验后立即 markUsed（status 0 → 1），用乐观锁
   - 如果 markUsed 成功，继续下单
   - 如果 markUsed 失败（0 行影响），说明被别人用了，直接报错
   - 订单失败时再 refund（改回 0）
   - 优点：实现简单，用户在下单阶段就知道结果
   - 缺点：下单失败要回退优惠券状态，且 markUsed 提前了（如果用户不支付，优惠券被占着）

**前端规避方案：**

1. **提交按钮防重复点击**：
   - 点击提交后立即禁用按钮，显示加载中
   - 防止用户手抖点两次
   - 这是最基本的，必须有

2. **预下单接口**：
   - 进入订单确认页时调用"预校验"接口，锁定优惠券和库存
   - 返回预下单号，支付时用预下单号确认
   - 预下单有有效期（如 15 分钟），超时自动释放
   - 用户体验最好，类似电商的"提交订单"→"去支付"两步走

3. **失败后友好提示**：
   - 如果因为优惠券问题下单失败，提示"优惠券已被使用，请重新选择"
   - 自动跳回购物车/订单确认页，让用户重新选优惠券
   - 而不是笼统的"下单失败"

**当前项目的最佳实践建议：**
- 短期：前端按钮防重复 + 失败友好提示
- 中期：提交订单时按 userCouponId 加锁（和库存扣减在同一个事务链路中）
- 长期：预下单 + 优惠券预占模式

### 75. DishService.checkAndDeduct() 返回 DishSnapshotVO 列表，OrderService 用这个列表构建 OrderItem。但如果菜品在库存扣减后立即被商家删除或下架，订单中的菜品快照已经包含了正确的名称和价格——这个"快照"是在哪个时间点取的？如果菜品价格在扣库存前 1 毫秒被修改，用户支付的是旧价还是新价？

**回答：**

**快照的时间点：**
- 快照是在 `checkAndDeduct()` 方法中，**扣减库存成功后**，从 `dishMap`（之前通过 `dishMapper.selectBatchIds` 查询的菜品数据）中取的
- 注意：dishMap 是扣减之前查的，不是扣减之后查的
- 所以快照的时间点是：**扣减库存操作之前的数据库查询时间点**

**价格修改的时序问题：**

场景：
1. 商家修改菜品价格（MySQL 更新）
2. 1 毫秒后，用户下单，执行 checkAndDeduct
3. selectBatchIds 查询菜品（读到新价格？还是旧价格？）
4. Lua 扣 Redis 库存
5. MySQL 扣库存
6. 用步骤 3 查到的价格生成快照

**如果商家修改价格和用户下单并发：**
- 取决于谁先执行数据库 UPDATE/SELECT
- 如果商家先更价格，用户后查 → 新价格 ✓
- 如果用户先查，商家后更 → 旧价格（但价格改了用户还按旧价下单）

**用户支付的是哪个价格：**
- 订单中的价格是下单时的价格（快照价格）
- 不管之后商家怎么改价，订单价格不变
- 这是合理的——用户下单时看到什么价，就按什么价成交

**但如果价格在"加入购物车"和"提交订单"之间变了：**
- 购物车中的价格可能是旧的
- 提交订单时，checkAndDeduct 重新从数据库查价格，生成快照
- 用户可能在购物车看到 10 元，提交订单后变成 12 元
- 这是正常的（购物车价格是缓存，以下单时为准），但应该在订单确认页显示最新价格并提示用户

**更极端的情况：菜品在扣库存前一瞬间被下架**
- checkAndDeduct 中先查菜品（状态 1=上架），然后扣 Redis 库存，然后 MySQL 扣库存
- 但菜品下架是改 status 字段，扣库存是改 stock 字段，不冲突
- 所以：查询时是上架的 → 扣库存成功 → 但菜品可能在中间被下架了
- 结果：用户下单成功了，但菜品是下架状态
- 这是一个小 bug（应该在扣库存的事务中再校验一次状态，或者下架时用分布式锁）

### 76. 整个项目没有一个统一的 ID 生成策略规范：订单号用 SnowflakeIdUtil，其他实体用 MySQL 自增（AUTO_INCREMENT）或 MyBatis-Plus 的 IdType.ASSIGN_ID。雪花 ID 和自增 ID 混用的优缺点是什么？ASSIGN_ID 默认使用的是哪种 ID 生成算法？

**回答：**

**雪花 ID vs 自增 ID 的对比：**

| 特性 | 自增 ID（AUTO_INCREMENT） | 雪花 ID（Snowflake） |
|------|------------------------|---------------------|
| 有序性 | 完全有序 | 趋势递增（同一毫秒内有序） |
| 分布式安全性 | 单点（MySQL 主库），扩容难 | 分布式安全，多节点生成不重复 |
| 暴露信息 | 容易被遍历（ID=1,2,3...） | 难以猜测，安全性好 |
| 存储空间 | 8 字节（BIGINT） | 8 字节（BIGINT） |
| 可读性 | 简单直观 | 长数字，不直观 |
| 性能 | 数据库自增，插入快 | 应用层生成，减少 DB 压力 |
| 回退/时钟回拨 | 无问题 | 有风险 |

**为什么混用：**
- 订单号用雪花 ID 是因为：
  1. 订单号需要全局唯一，未来拆微服务也能用
  2. 不暴露订单量（自增 ID 能被猜出台阶）
  3. 订单号生成要快，不能等数据库自增
- 其他表用自增是因为：
  1. 简单，不需要额外配置
  2. 内部 ID，不需要对外暴露
  3. MyBatis Plus 开箱即用

**MyBatis-Plus 的 ASSIGN_ID 是什么算法：**
- 默认使用 **雪花算法（Snowflake）** 的变体
- `IdentifierGenerator` 接口的默认实现是 `DefaultIdentifierGenerator`
- 内部用的是 `Sequence` 类，就是雪花算法的实现
- 但默认的 workerId 和 dataCenterId 是怎么来的？
  - 默认会读取机器的网卡地址等信息生成，也可以手动配置
  - 单节点没问题，分布式部署需要手动配置不同的 workerId

**混用的缺点：**
1. **不统一**：有些表是自增，有些是雪花，开发者容易搞混
2. **ID 长度差异**：雪花 ID 是 19 位长数字，自增 ID 初期很短
3. **拆库拆表困难**：自增 ID 在分库分表时需要全局 ID 生成器
4. **Long 转 String**：所有 ID 都要转字符串给前端，不管长短

**建议：**
- 统一用雪花 ID（或 ASSIGN_ID），所有表主键都用分布式 ID
- 一开始就统一，避免后期改造成本
- 订单号可以和主键 ID 是同一个，也可以分开（订单号有业务含义的话分开）

---

## 十六、代码质量与维护

### 77. OrderService 的 ready() 方法将订单从 3（备餐中）流转到 5（配送中），跳过了 4（待取餐）。但 swagger 注释说"将订单从备餐中流转到待取餐"，状态却直接跳到了配送中——这是代码注释不一致还是逻辑有意为之？注释和代码哪个错了？

**回答：**

**分析：**
- 代码：`updateStatusWithLock(order, 3, 5, null)` —— 从 3（备餐中）→ 5（配送中）
- 数据库 comment：status 字段注释是"2=待接单 3=备餐中 5=配送中 6=已完成 7=已取消"
- 注意：数据库注释里也没有 status=4！
- 所以代码和数据库是一致的，没有 4 这个状态值

**那注释说"待取餐"是怎么回事：**
- 注释错了。可能是早期设计有"待取餐"状态（4），后来砍掉了，注释没更新
- 或者是复制粘贴时搞错了

**为什么没有"待取餐"状态：**
- 外卖系统的状态流转通常是：待支付 → 待接单 → 备餐中 → 配送中 → 已完成
- "待取餐"通常是到店自取模式才有的状态
- 当前系统可能只做了配送模式，所以跳过了待取餐
- 如果以后加"到店自取"，需要加 4=待取餐

**注释和代码哪个错了：**
- **注释错了**。代码和数据库定义一致，都是 3→5，没有 4
- 这是典型的"代码迭代了但文档/注释没跟上"

**修复建议：**
1. 修正注释：`将订单从备餐中流转到配送中`
2. 或者加上待取餐状态（如果业务需要）
3. 状态流转相关的注释/文档要和代码同步更新

### 78. BusinessException 有两个构造器：BusinessException(String message) 和 BusinessException(ResultCode, String message)，第一个构造器默认使用 BUSINESS_ERROR(600)。许多调用处使用了 new BusinessException(ResultCode.PARAM_ERROR, "xxx")，但也有直接用 BusinessException("xxx") 的。前者是参数错误（400），后者是业务异常（600）——这种混用会不会导致前端根据 code 做判断时出现误判？

**回答：**

**会不会导致前端误判：**
- **会的**。前端通常根据 code 做不同处理：
  - 400（PARAM_ERROR）：参数错误，提示用户检查输入（表单标红等）
  - 600（BUSINESS_ERROR）：业务异常，弹窗提示
  - 401（UNAUTHORIZED）：未登录，跳转登录页
- 如果该是参数错误的地方用了默认的 600，前端就不会高亮表单字段，用户体验差
- 反过来，如果该是业务异常的地方用了 400，前端可能以为是用户输入错了

**混用的原因：**
1. **开发习惯**：有些开发者嫌麻烦，直接抛 `new BusinessException("xxx")`
2. **错误码意识不强**：不觉得 400 和 600 有什么区别，反正都是"报错"
3. **没有规范**：团队没有约定什么时候用什么错误码
4. **快速开发**：赶时间时怎么快怎么来

**有什么风险：**
1. **前端交互逻辑混乱**：该提示表单错误的地方弹了通用弹窗
2. **问题排查困难**：看错误码分不清是参数问题还是业务问题
3. **API 契约不清晰**：前端无法根据错误码做统一处理

**改进方案：**

1. **丰富错误码**：
   - 不只有 PARAM_ERROR 和 BUSINESS_ERROR，再细分（如 COUPON_EXPIRED、STOCK_NOT_ENOUGH 等）
   - 前端可以根据具体错误码做不同处理

2. **代码规范**：
   - 禁止直接使用 `new BusinessException("xxx")`（默认 600）
   - 必须指定 ResultCode
   - 用静态代码检查或 Code Review 保证

3. **参数错误统一在 Controller 层处理**：
   - 参数格式错误用 @Valid + 全局异常处理器自动返回 400
   - Service 层只抛业务异常（600）
   - 分层明确，减少混用

4. **错误码定义文档**：
   - 维护一份错误码字典，前端后端都参考

### 79. OrderService.toVO() 中 merchantService.getInternal(o.getMerchantId()).getName() 用 try-catch 吞掉了所有异常。如果商家被删除导致查询抛出异常，merchantName 会静默变为 null——前端显示 null 还是空字符串？调用者完全不知道这里可能失败。

**回答：**

**前端显示 null 还是空字符串：**
- 取决于前端怎么处理。如果直接 `{{ order.merchantName }}`，Vue 会显示空（不显示 null 字符串）
- 如果用 `console.log(order.merchantName)`，会打印 null
- 但如果前端有字符串拼接或比较，可能出现 "商家：null" 这样的显示
- 总之，null 显示给用户是不友好的

**更严重的问题：**

1. **静默失败**：
   - 商家不存在是数据异常，应该被发现并修复
   - 但 try-catch 吞掉后，日志里没有记录，开发/运维完全不知道
   - 这是"掩盖问题"而不是"处理问题"

2. **为什么商家会被删除：**
   - 商家表有逻辑删除（deleted 字段），getInternal 用 selectById，MyBatis Plus 会自动过滤 deleted=1
   - 所以如果商家被删除了（deleted=1），selectById 返回 null
   - 然后 `null.getName()` 抛 NPE，被 catch 吞掉

3. **try-catch 范围过大**：
   - `catch (Exception ignored)` 捕获了所有异常，包括：
     - 商家不存在（预期内的异常？）
     - 数据库连接异常
     - Redis 缓存异常
     - 其他未知 RuntimeException
   - 所有异常都被吞了，真正的系统异常也发现不了

**改进建议：**

1. **不要用 try-catch 吞异常**：
   - 商家不存在是业务问题，应该在 Service 层处理（返回默认名称，如"商家已关闭"）
   - 系统异常应该抛出，由全局异常处理器处理

2. **更优雅的空值处理：**
   ```java
   String merchantName = merchantService.getInternal(o.getMerchantId()).getName();
   // 改成：
   Merchant merchant = merchantService.getInternalSafely(o.getMerchantId());
   String merchantName = merchant != null ? merchant.getName() : "商家已关闭";
   ```

3. **至少打日志**：
   - 就算要降级处理，也要打 warn 日志，便于后续排查
   - `catch (Exception e) { log.warn("获取商家名称失败，orderNo={}", o.getOrderNo(), e); }`

4. **数据完整性约束**：
   - 订单的 merchant_id 应该是外键（虽然很多项目不用外键）
   - 商家删除时检查是否有未完成的订单
   - 或者商家只能"关闭"不能"删除"，保证历史订单的商家信息可查

### 80. 项目里存在多个 @SuppressWarnings("unchecked")，尤其是在 Redis 反序列化处。这些 unchecked 警告实质上是运行时类型安全的漏洞——如果 Redis 中的数据被错误写入或其他应用污染了同一个 Redis 的 key，反序列化会在哪里抛出异常？ClassCastException 会被 GlobalExceptionHandler 捕获吗？

**回答：**

**如果 Redis 数据被污染，反序列化会怎样：**

场景 1：类型不匹配（如存了 String 但按 List 取）
- `redisUtil.get(key)` 返回 Object，实际类型是 String
- 然后 `(List<MenuCategoryVO>) cached` 强转
- **注意**：Java 的泛型是编译期擦除的，运行时 `List<MenuCategoryVO>` 就是 List
- 所以强转成 List 不会抛 ClassCastException（只要 cached 是 List 类型）
- 但如果 cached 不是 List（比如是 String），强转 List 会抛 ClassCastException

场景 2：泛型参数类型错误（如 List<MerchantVO> 被当成 List<MenuCategoryVO>）
- 因为泛型擦除，强转 List<MenuCategoryVO> 不会抛异常
- 但当你访问列表中的元素并调用 MenuCategoryVO 的方法时，可能抛 ClassCastException
- 这是延迟抛出的，更难排查

**会在哪里抛出异常：**
- 如果是 RedisTemplate 的反序列化阶段：Jackson 反序列化失败会抛 JsonMappingException 等
- 如果是 Java 强转阶段：ClassCastException 通常在使用泛型元素时抛出

**GlobalExceptionHandler 能捕获吗：**
- `@ExceptionHandler(Exception.class)` 可以捕获 ClassCastException（因为它继承自 RuntimeException → Exception）
- 但会走到通用的 handleException 分支，返回"系统繁忙"
- 日志中会记录错误堆栈

**Redis 数据被污染的风险：**
1. **开发/测试环境共用 Redis**：多个项目用同一个 Redis，key 冲突
2. **版本不兼容**：代码升级后，旧缓存数据结构不匹配
3. **Redis 被攻击**：未授权访问，攻击者篡改数据
4. **操作失误**：运维手动执行 Redis 命令改错数据

**防御措施：**

1. **Redis 数据库隔离**：不同环境用不同的 database（0-15），或不同的 Redis 实例
2. **key 前缀规范**：每个项目的 key 有统一前缀（如 `takeout:`），避免冲突
3. **try-catch + 降级**：像 MerchantService.getDetail() 那样，反序列化失败就删缓存回源 DB
4. **Jackson 反序列化配置**：配置 FAIL_ON_UNKNOWN_PROPERTIES 等，增加容错
5. **Redis 密码 + 防火墙**：防止未授权访问

**当前代码的问题：**
- DishService.getMenu() 中只有 `@SuppressWarnings("unchecked")`，没有 try-catch
- 如果菜单缓存数据结构不匹配，会直接抛异常，返回 500
- 应该和 MerchantService.getDetail() 一样，加 try-catch + 清除缓存 + 回源 DB

---

## 十七、安全与生产化

### 81. 整个项目没有接口限流保护。如果某个商家被恶意刷单（每秒 1000 次下单请求），OrderService.submit() 中的 Redis 扣库存和 MySQL 写入能扛住吗？需要怎么加限流？

**回答：**

**能扛住 1000 QPS 吗：**
- **Redis 层面**：单机 Redis 处理 1000 QPS 的 Lua 脚本问题不大（Redis 能扛几万 QPS）
- **MySQL 层面**：下单涉及多表写入（订单、订单明细、购物车删除、库存扣减、优惠券核销），1000 QPS 可能扛不住（MySQL 单机写入通常几百到几千 TPS，取决于事务大小）
- 更关键的是：恶意刷单不是为了压垮系统，而是为了占用库存、生成垃圾订单
- 即使系统扛住了，商家的库存也会被恶意订单占满，正常用户买不了

**需要加的限流措施：**

**1. 用户级别限流**（防单个用户刷）：
- 同一用户每秒最多 N 次下单请求（如 1 次/秒）
- 同一用户每天最多 N 个订单
- 基于用户 ID 限流，用 Redis + 拦截器实现

**2. IP 级别限流**（防单 IP 刷）：
- 同一 IP 每秒最多 N 次请求
- 防止攻击者用脚本刷

**3. 接口级别限流**（保护整体系统）：
- 下单接口整体限流（如 100 QPS），超过直接拒绝
- 保护数据库不被打垮

**4. 验证码/人机验证**：
- 高频下单要求输入图形验证码
- 防止自动化脚本攻击

**5. 库存预扣 + 超时释放**：
- 下单后库存锁定 15 分钟，超时未支付自动释放
- 防止恶意下单占库存

**6. 风控规则**：
- 同一设备、同一收货地址、同一手机号的异常下单行为
- 新注册用户下单限制

**限流实现方案：**

- **快速实现**：Redis + 自定义注解 + AOP，自己写限流逻辑
- **推荐方案**：引入 Redisson，内置 RateLimiter（令牌桶算法）
- **专业方案**：引入 Sentinel，支持流量控制、熔断降级、系统自适应保护
- **网关层限流**：如果有网关（如 Nginx、Spring Cloud Gateway），在网关层限流更高效

**当前项目的建议：**
- 初期可以先加简单的 Redis 限流（按用户 + 按 IP）
- 流量上来后再引入 Sentinel

### 82. JWT 的签名密钥硬编码，如果泄露，攻击者可以伪造任意用户身份的 Token。如何鉴定 JWT secret 的安全性？有没有定期轮换机制？

**回答：**

**如何鉴定 JWT secret 的安全性：**

1. **长度检查**：
   - HMAC-SHA256 要求密钥至少 32 字节（256 位）
   - 当前 secret 是 44 字符（"takeout-system-jwt-secret-key-2024-very-long"），长度够

2. **熵值/复杂度**：
   - 当前密钥是有意义的英文短语，熵值低，容易被暴力破解（字典攻击）
   - 安全的密钥应该是随机字符串（大小写字母 + 数字 + 特殊字符）

3. **存储方式**：
   - 硬编码在 yml 中，代码仓库泄露即密钥泄露
   - 安全的方式：环境变量、配置中心（加密存储）、KMS

4. **传播范围**：
   - 知道密钥的人越少越安全
   - 当前所有开发者都能看到（代码仓库里）

**为什么需要定期轮换：**
- 即使密钥没泄露，定期轮换也是安全最佳实践（纵深防御）
- 万一密钥悄悄泄露了，轮换可以缩小损失时间窗口

**密钥轮换机制设计：**

1. **多密钥验证（带 kid）**：
   - JWT header 中加入 kid（Key ID）字段
   - 服务端维护密钥列表（当前生效密钥 + 历史密钥）
   - 解析时根据 kid 找对应的密钥验证
   - 新增密钥时，旧 Token 仍然可以用旧密钥验证

2. **渐进式轮换**：
   - 阶段 1：加入新密钥，新旧密钥同时可验证（Token 仍用旧密钥签发）
   - 阶段 2：切换签发密钥为新密钥（旧密钥只用于验证）
   - 阶段 3：等待旧 Token 全部过期后，移除旧密钥

3. **刷新 Token 时自动升级**：
   - 用户刷新 accessToken 时，用新密钥签发新的 token 对
   - 用户无感知完成密钥轮换

**当前项目的现状：**
- 密钥硬编码，没有轮换机制
- 建议：
  1. 把密钥移到环境变量：`jwt.secret: ${JWT_SECRET:default-dev-secret}`
  2. 生产环境通过环境变量注入真实密钥
  3. 定期（如每 3 个月）更换一次密钥
  4. 未来业务做大后考虑多密钥轮换机制

### 83. SQL 注入：DishService.checkAndDeduct() 通过 LambdaUpdateWrapper.setSql() 拼接了 "stock = stock - " + item.quantity()，虽然 quantity 是 int 安全，但项目中是否存在其他地方直接拼接字符串参数到 SQL 的情况？

**回答：**

**当前代码中是否有 SQL 注入风险：**

已确认的：
- `DishService.checkAndDeduct()` 中的 `setSql("stock = stock - " + item.quantity() + ", sales = sales + " + item.quantity())` —— quantity 是 int，安全
- `DishService.revertStock()` 中的类似拼接 —— 同样是 int，安全

**需要排查的潜在风险点：**

1. **所有 setSql 调用**：搜索 `.setSql(` 看是否有字符串参数拼接
2. **自定义 XML SQL**：DishMapper.xml 和 MerchantMapper.xml 中是否有 `${}`（不是 `#{}`）
   - `${}` 是直接拼接，有注入风险
   - `#{}` 是预编译参数，安全
3. **QueryWrapper 中的 apply 方法**：`.apply("xxx = " + value)` 也是拼接
4. **order by 参数**：如果排序字段是前端传的，可能注入（ORDER BY 后面不能用预编译参数）

**为什么会有 setSql 拼接数字的写法：**
- MyBatis Plus 的 LambdaUpdateWrapper 不直接支持"字段 = 字段 + 值"这种表达式赋值
- 只能用 setSql 来写 SQL 片段
- 数字类型的拼接是安全的，但字符串类型不行

**如何保证安全：**

1. **代码审查规则**：
   - 禁止用字符串变量拼接 setSql/apply
   - 数字常量/变量可以，字符串必须用参数化方式

2. **统一封装**：
   - 封装一个安全的 setSql 工具方法，自动处理参数化
   - 或者用 MyBatis Plus 的 `setSql("col = col - {0}", value)` 方式（支持参数化）

3. **静态扫描**：
   - 用 SonarQube 等工具扫描 SQL 注入风险
   - 自定义规则检测 setSql 中的字符串拼接

4. **XML 中只用 #{}**：
   - 所有自定义 SQL 用 `#{}` 传参
   - 动态表名/列名用白名单校验，不能直接拼接用户输入

**总体评估：**
- 从已看的代码来看，项目中 SQL 注入风险较低
- 主要的 setSql 都是操作数字字段，没有字符串拼接
- 但仍需要全面排查所有自定义 SQL 和拼接操作

### 84. 所有接口没有操作日志（Audit Log）。管理员禁用了某个用户、商家拒绝了一个订单——这些操作没有留痕。如果要加操作日志，用什么方案侵入最小？AOP 注解会不会漏掉一些路径？

**回答：**

**为什么需要操作日志：**
1. **安全审计**：谁在什么时候做了什么操作，出问题可追溯
2. **问题排查**：用户投诉时，可以查看操作历史定位问题
3. **合规要求**：某些行业（金融、支付）强制要求操作日志
4. **数据分析**：分析运营人员的操作行为

**侵入最小的方案：**

**方案一：AOP + 自定义注解（推荐）**
- 定义 `@OperateLog` 注解，标注在需要记录日志的方法上
- 用 AOP 切面拦截所有带该注解的方法，记录操作人、操作时间、方法名、参数、结果
- 优点：侵入性小，只需要在方法上加注解
- 缺点：需要手动加注解，可能遗漏

**方案二：数据库日志（Binary Log / CDC）**
- 监听 MySQL binlog，解析数据变更（INSERT/UPDATE/DELETE）
- 用 Canal/Debezium 等工具同步到日志系统
- 优点：完全无侵入，不需要改业务代码
- 缺点：只能记录数据变更，不能记录"谁操作的"（应用层的用户信息 binlog 里没有）
- 缺点：读操作记录不到

**方案三：拦截器 + 统一入口**
- 在 Spring MVC 拦截器或 Filter 中记录所有请求
- 记录请求路径、参数、操作用户、IP 等
- 优点：覆盖所有接口，不需要手动加注解
- 缺点：记录的信息比较粗，不知道具体操作了什么业务
- 缺点：读操作也会记录，日志量大

**AOP 注解会不会漏掉：**
- 会的。如果开发者忘了加注解，就不会记录
- 解决办法：
  1. 制定规范，要求所有写操作接口必须加 @OperateLog
  2. Code Review 时检查
  3. 配合拦截器做兜底（记录所有写操作的请求日志）

**推荐方案：**
- 短期：AOP + 自定义注解，先覆盖管理员操作、商家关键操作
- 长期：数据库 binlog + 应用层日志结合，binlog 记录数据变更，应用层记录操作人

---

### 85. 项目的错误信息直接返回到前端（如"订单不在待支付状态"），在商业环境中，错误信息过多会暴露业务逻辑细节，过少不利于调试。当前的粒度是否适合生产环境？

**回答：**

**当前错误信息粒度分析：**

| 错误类型 | 返回给前端的信息 | 是否暴露业务细节 |
|---------|----------------|----------------|
| 参数错误 | "参数校验失败: xxx" | 安全，告诉用户哪里输错了 |
| 业务异常 | "订单不在待支付状态"、"优惠券已使用" | 暴露业务状态流转逻辑 |
| 系统异常 | "系统繁忙，请稍后重试" | 安全，不暴露内部细节 |
| 数据库/Redis异常 | "数据库连接失败"、"Redis 连接失败" | 暴露技术栈信息 |

**是否适合生产环境：**
- **业务错误信息偏细**：如"订单不在待支付状态"暴露了订单状态机的细节，攻击者可以通过枚举了解系统状态流转
- **技术错误信息偏细**："数据库连接失败"、"Redis 连接失败"直接告诉攻击者后端用了什么技术栈，便于针对性攻击
- **缺少错误追踪ID**：用户报错时只有文字，没有 traceId，开发排查困难

**生产环境建议：**

1. **业务错误信息分级：**
   - 用户可理解的：保留，如"优惠券已过期"
   - 内部状态流转的：模糊化，如"订单状态不支持此操作"（不说具体是什么状态）

2. **技术错误全部脱敏：**
   - 数据库/Redis 连接异常统一返回"系统繁忙，请稍后重试"
   - 具体错误详情只记日志，不返回前端

3. **增加 traceId：**
   - 每个请求生成唯一 traceId，错误响应中带上
   - 用户报错时报 traceId，开发根据 traceId 查日志

4. **区分内外网：**
   - 内网/测试环境可以返回详细错误信息
   - 生产环境只返回用户友好的提示 + traceId

---

## 十八、构建与依赖管理（Build）

### 86. pom.xml 中依赖管理使用了 <version> 硬编码，但 mysql-connector-j、spring-boot-starter-data-redis 等依赖没有指定版本，完全依赖 Spring Boot 3.2.5 的 BOM。如果未来 Spring Boot 升级到 3.3.x，哪些依赖版本会自动更新、哪些不会？spring-boot-starter-parent 的 BOM 覆盖范围有没有明确的规则？

**回答：**

**哪些会自动更新，哪些不会：**

**自动随 Spring Boot 升级的（BOM 管理的）：**
- `spring-boot-starter-web`、`spring-boot-starter-validation`、`spring-boot-starter-data-redis`
- `mysql-connector-j`（Spring Boot BOM 管理）
- `spring-boot-starter-test`
- `lombok`（Spring Boot BOM 管理）
- 所有 Spring 官方 starter 及其传递依赖

**不会自动更新的（手动指定版本的）：**
- `mybatis-plus-spring-boot3-starter`（版本 3.5.7，手动指定）
- `jjwt-api`、`jjwt-impl`、`jjwt-jackson`（版本 0.12.5，手动指定）
- `knife4j-openapi3-jakarta-spring-boot-starter`（版本 4.5.0，手动指定）

**spring-boot-starter-parent 的 BOM 覆盖规则：**
- 覆盖所有 `spring-boot-starter-*` 系列依赖
- 覆盖常用的第三方库：MySQL、PostgreSQL、H2、Redis 客户端、Jackson、Logback、Lombok 等
- 完整列表在 `spring-boot-dependencies` 的 pom 中定义
- 规律：只要是 Spring 官方生态推荐的、常用的库，基本都在 BOM 里

**升级 Spring Boot 的风险：**
- BOM 管理的依赖版本会变，但 API 通常兼容（Spring 很注重向后兼容）
- 手动管理的依赖（MyBatis-Plus、Knife4j、JJWT）可能和新版 Spring Boot 不兼容
- 比如 Spring Boot 3.3.x 可能要求更高版本的 MyBatis-Plus

**最佳实践：**
- 能交给 Spring Boot BOM 管理的就不要手动指定版本
- 第三方库的版本要注意和 Spring Boot 版本的兼容性
- 升级 Spring Boot 前先查第三方库的兼容矩阵

---

### 87. 项目依赖了 jjwt-api（compile 范围）、jjwt-impl（runtime）、jjwt-jackson（runtime）三个 JJWT 包。jjwt-impl 和 jjwt-jackson 设为 runtime 范围是标准做法，但 jjwt-jackson 是 JJWT 的 JSON 序列化实现——项目里同时有 Jackson 和 JJWT 两套 JSON 处理。如果 objectMapper 的配置（如 Long→String 序列化）被 JJWT 内部调用时也会生效吗？JJWT 使用的 ObjectMapper 和 Spring 容器中的是同一个实例吗？

**回答：**

**JJWT 使用的 ObjectMapper 和 Spring 的是同一个吗：**
- **不是同一个实例**
- JJWT 的 `jjwt-jackson` 模块内部会自己创建一个 `ObjectMapper` 实例
- 它不会从 Spring 容器中获取 ObjectMapper
- 所以 Spring 的 Jackson 配置（Long→String、日期格式等）**不会**影响 JJWT 的 JSON 序列化

**为什么 jjwt-jackson 要自己造 ObjectMapper：**
- JJWT 是一个通用库，不依赖 Spring
- 它需要在任何 Java 环境下都能工作，不只是 Spring 项目
- 所以它内部自己管理 ObjectMapper

**两套 JSON 处理的潜在问题：**
1. **配置不一致**：Spring 的 ObjectMapper 配置了 `Long→String`，但 JJWT 的没有
2. **序列化行为差异**：比如日期格式、空值处理等可能不一样
3. **资源浪费**：两个 ObjectMapper 实例，占用少量内存

**JWT claims 中的 Long 会怎样：**
- `userId` 是 Long 类型，JJWT 用自己的 ObjectMapper 序列化
- 在 JWT 的 JSON 中，Long 会被序列化为数字（不是字符串）
- 解析时也是数字，所以 `claims.get("userId")` 返回的可能是 `Integer` 或 `Long`
- 这就是为什么 `JwtUtil.getUserId()` 和 `AuthInterceptor.extractUserId()` 中都有 `instanceof Integer` → `longValue()` 的判断

**如果想让 JJWT 使用 Spring 的 ObjectMapper：**
- 可以自定义 `JwtParser` 的 `json()` 方法，传入 Spring 的 ObjectMapper
- 但比较麻烦，一般没必要
- JWT 的 claims 内容很简单，用默认的 Jackson 配置足够

**为什么 jjwt-impl 和 jjwt-jackson 是 runtime 范围：**
- 这是 JJWT 的官方推荐做法
- 编译时只依赖 API 包（接口），运行时才需要实现
- 符合"面向接口编程"原则，未来可以替换实现

---

### 88. Maven 打包配置了 <finalName>takeout-app</finalName>，但没有配置 maven-compiler-plugin 的 -parameters 参数相关的额外 flag。Spring Boot 3.x 的参数名保留需要通过 javac -parameters 或者 <parameters>true</parameters> 设置。pom.xml 中已经有 <parameters>true</parameters> 了——去掉这一行后，@RequestParam 和 @PathVariable 的参数名解析会出什么问题？Spring Boot 3.x 对此的配置与 2.x 有什么不同？

**回答：**

**先澄清：当前 pom.xml 中是否有 <parameters>true</parameters>：**
- 从已读取的 pom.xml 前 100 行来看，没有看到显式的 `<parameters>true</parameters>` 配置
- 但 Spring Boot 3.x 的 `spring-boot-starter-parent` 默认就开启了 `-parameters`
- 所以即使不显式配置，参数名也会被保留

**去掉 -parameters 会出什么问题：**

**场景 1：@RequestParam 不指定 value 属性**
```java
// 有 -parameters 时，参数名 "id" 会被保留，Spring 能识别
@GetMapping("/test")
public String test(String id) { ... }

// 没有 -parameters 时，参数名变成 arg0、arg1，Spring 找不到
// 会报错：Required parameter 'id' is not present
```

**场景 2：@PathVariable 不指定 value 属性**
```java
// 同理，没有 -parameters 时无法解析参数名
@GetMapping("/user/{id}")
public User getUser(@PathVariable Long id) { ... }
```

**场景 3：MyBatis-Plus 的 LambdaQueryWrapper**
- 不受影响，因为 Lambda 是编译期生成的，不依赖参数名保留

**Spring Boot 3.x vs 2.x 的区别：**

| 特性 | Spring Boot 2.x | Spring Boot 3.x |
|------|----------------|----------------|
| 默认 -parameters | 部分版本默认开启 | 默认开启（parent POM 配置）|
| 参数名发现策略 | 先调 -parameters，不行就用调试信息 | 优先 -parameters |
| Java 版本要求 | Java 8+ | Java 17+ |

**为什么 -parameters 很重要：**
- Java 8 之前，编译后的 class 文件不保留方法参数名
- Java 8 引入了 `-parameters` 编译参数，可以保留参数名
- Spring MVC / Spring Boot 大量依赖参数名自动绑定
- 没有参数名的话，每个 @RequestParam、@PathVariable 都要手动写 value 属性，很啰嗦

**验证当前项目是否开启了 -parameters：**
- 可以看编译后的 class 文件，用 `javap -verbose` 查看是否有 MethodParameters 属性
- 或者直接写个不带 value 的 @RequestParam 测试

---

### 89. 项目没有 spring-boot-maven-plugin 的 layers 配置（<layers>），Dockerfile 使用的是两阶段构建（build→jar copy）。如果使用 Spring Boot 3.x 的镜像层（layers）功能，Docker 镜像构建能获得什么好处？当前 Dockerfile 中 COPY pom.xml && RUN mvn dependency:go-offline 是否真的能缓存依赖层？mvn dependency:go-offline 有哪些场景会漏掉依赖？

**回答：**

**Spring Boot layers 功能的好处：**

1. **镜像分层更细**：
   - 不加 layers：整个 fat jar 是一个层，代码改一点整个层都要重新构建
   - 加 layers：fat jar 被拆分为多个层（dependencies、spring-boot-loader、snapshot-dependencies、application）
   - 依赖层很少变，应用层经常变，构建时可以复用缓存

2. **构建速度提升**：
   - 依赖层（dependencies）几百 MB，代码层（application）可能只有几百 KB
   - 代码改动后，只需要重建 application 层，前面的层都用缓存
   - 尤其是 CI/CD 环境下，效果明显

3. **镜像拉取更快**：
   - 部署新版本时，只拉取变化的层（通常是 application 层）
   - 节省网络传输时间

**当前 Dockerfile 的缓存策略是否有效：**

```dockerfile
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn package -DskipTests
```

- **理论上是对的**：先复制 pom.xml，下载依赖，再复制源码编译
- 这样 pom.xml 不变的话，依赖层可以复用缓存
- **但 `dependency:go-offline` 有坑**

**mvn dependency:go-offline 会漏掉依赖的场景：**

1. **plugin 依赖**：
   - `dependency:go-offline` 只下载项目的依赖，不下载插件的依赖
   - 比如 `spring-boot-maven-plugin` 的依赖不会被下载
   - 真正 `mvn package` 时还需要联网下载插件

2. **runtime / test scope 的依赖**：
   - 某些 scope 的依赖可能不会被 go-offline 下载
   - 取决于 Maven 版本和参数

3. **传递依赖的版本不一致**：
   - 有时候 go-offline 下载的版本和实际构建时选择的版本不一致

4. **SNAPSHOT 依赖**：
   - SNAPSHOT 版本会频繁更新，go-offline 下载的可能不是最新的

**更可靠的方案：**
- 使用 `mvn dependency:go-offline -DexcludeGroupIds=org.apache.maven.plugins` 等参数
- 或者直接 `COPY pom.xml && RUN mvn -B de.qaware.maven:go-offline-maven-plugin:resolve-dependencies`
- 或者用 Spring Boot 的 layers 功能，从构建好的 jar 中提取层

**当前项目的情况：**
- 单体项目，依赖不多，就算不加 layers 也能接受
- 但如果构建频率高，加 layers 还是能省不少时间
- 建议：加上 `<layers><enabled>true</enabled></layers>` 配置，Dockerfile 用分层复制

---

## 十九、Docker 与部署（Deployment）

### 90. Docker 镜像推送至 GHCR，但 docker-compose.yml 中 backend 服务同时配置了 build 和 image——docker compose up 时是优先使用本地构建的镜像还是优先从 GHCR 拉取？多人在同一台机器上开发时，这种配置会导致什么混乱？

**回答：**

**docker compose up 时的优先级：**

- **如果本地已经有该 image 名称的镜像**：直接用本地镜像，不会重新 build 也不会拉取
- **如果本地没有该镜像**：
  - 默认 `docker compose up` 会**先尝试拉取**（从 GHCR），如果拉取失败再 build
  - 用 `docker compose up --build` 会**强制重新 build**，忽略远程镜像
  - 用 `docker compose up --pull always` 会**强制拉取**最新镜像

简单说：**本地镜像 > 拉取 > 构建**（默认行为）

**同时配置 build 和 image 的含义：**
- `build`：告诉 Docker 怎么构建这个镜像
- `image`：构建后给镜像起的名字（标签）
- 如果镜像名和远程仓库一致，`docker compose push` 可以推送

**多人同机开发的混乱：**

1. **镜像覆盖问题**：
   - A 修改了代码，执行 `docker compose up --build`，构建了 `ghcr.io/xxx/takeout-backend:latest`
   - B 也在同一台机器上，`docker compose up` 直接用了 A 构建的镜像
   - B 以为自己的代码在运行，实际跑的是 A 的代码

2. **版本混乱**：
   - 两个人的代码版本不一样，但都打同一个标签 `latest`
   - 谁最后 build，镜像就是谁的

3. **数据卷冲突**：
   - 如果用了 named volume（如 mysql-data），两个人共用同一个数据库
   - A 改了表结构，B 的代码可能报错

4. **端口冲突**：
   - 都要占用 8080、3306、6379 端口，第二个启动的会报错

**正确的多人同机开发方式：**
1. **每个人用不同的项目目录和 compose 项目名**
   - `docker compose -p user1 up` 和 `docker compose -p user2 up`
2. **端口映射错开**：A 用 8081，B 用 8082
3. **不要在开发机上共享镜像标签**：本地构建的镜像用不同的 tag

**生产环境的 docker-compose.yml 建议：**
- 生产环境的 compose 文件只配 `image`，不配 `build`
- 开发环境的 compose 文件可以配 `build`
- 用不同的 compose 文件区分环境：`docker-compose.yml` + `docker-compose.override.yml`

---

### 91. application-docker.yml 只覆盖了 spring.datasource.url 和 redis.host，其余配置与 application.yml 完全继承。但 application.yml 中的 logging.level.com.takeout: DEBUG 在生产环境不应该用 DEBUG 级别——Docker 部署通常面向生产，application-docker.yml 应该同时覆盖日志级别吗？为什么没有做？

**回答：**

**为什么没有覆盖日志级别：**
- 大概率是**疏忽了**，不是有意为之
- 开发时方便调试，直接用了 DEBUG，部署时忘了改
- 很多项目都会犯这个错："反正先跑起来再说，日志以后再调"

**生产环境用 DEBUG 日志的危害：**

1. **日志量爆炸**：
   - DEBUG 级别会输出大量 SQL、参数、返回值
   - 一个下单请求可能打出几十上百行日志
   - 100 QPS 的话，每秒几万行日志，磁盘很快就满了

2. **性能影响**：
   - 写日志也是 IO，DEBUG 日志多了会拖慢响应
   - 虽然 SLF4J 有占位符优化，但还是有开销

3. **敏感信息泄露**：
   - DEBUG 日志可能打印用户手机号、地址等敏感信息
   - 如果日志文件被窃取，会导致数据泄露

4. **排查问题困难**：
   - 日志太多，找 ERROR 和 WARN 像大海捞针

**为什么 application-docker.yml 不适合作为"生产环境"配置：**
- 名字叫 `docker`，不是 `prod`
- 它的定位可能是"Docker 环境下的开发/测试配置"，不是生产配置
- 真正的生产配置应该叫 `application-prod.yml`

**建议的配置分层：**

| Profile | 用途 | 日志级别 |
|---------|------|---------|
| default (application.yml) | 本地开发 | DEBUG |
| docker | Docker  compose 部署（开发/测试）| INFO |
| prod | 生产环境 | WARN / ERROR |

**正确的做法：**
- `application-docker.yml` 中加上 `logging.level.com.takeout: INFO`
- 再创建一个 `application-prod.yml`，日志级别 WARN，其他生产配置
- 部署时通过 `SPRING_PROFILES_ACTIVE=prod` 指定

---

### 92. Docker 后台服务的健康检查仅覆盖 MySQL 和 Redis 的端口可达性，但 backend 服务自身的 depends_on 只等待 service_healthy。如果数据库连接需要初始化表结构（init.sql 挂载到 docker-entrypoint-initdb.d），MySQL 初始化脚本执行需要时间，backend 在 MySQL 刚返回 mysqladmin ping 成功但表还没创建完时就启动了，会发生什么？Spring Boot 的启动时 DataSource 初始化失败会触发 restart: unless-stopped 的重启循环吗？

**回答：**

**MySQL 健康检查和表初始化的时序：**

1. MySQL 容器启动
2. MySQL 服务启动，端口 3306 可连接（此时健康检查返回 healthy）
3. 执行 `docker-entrypoint-initdb.d` 下的 SQL 脚本（建库、建表、插数据）
4. 脚本执行完成，MySQL 完全就绪

**问题：**
- 步骤 2 完成时，健康检查就认为 MySQL 是 healthy 了
- 但步骤 3 的 SQL 脚本可能还在执行中
- 这时 backend 启动，连接 MySQL 发现表不存在，就会报错

**depends_on 的 service_healthy 够吗：**
- **不够**。端口可连接 ≠ 数据就绪
- MySQL 健康检查如果只检查端口，会出现"假健康"
- 需要等 init 脚本执行完才算真正就绪

**Spring Boot 启动时表不存在会怎样：**
- MyBatis-Plus 启动时不会自动建表（不像 JPA 的 hibernate.ddl-auto）
- 所以启动阶段不会报错，应用能正常启动
- 但**第一次访问数据库时**（比如用户登录、查商家列表），会抛出 `Table 'xxx' doesn't exist` 异常
- 如果是启动时就访问数据库的 Bean（比如某些初始化器），会导致启动失败

**restart: unless-stopped 会触发重启循环吗：**
- 如果应用启动失败（退出码非 0），Docker 会重启
- 如果应用启动成功但运行时报错（退出码 0 或不退出），Docker 不会重启
- 当前项目：MyBatis-Plus 不自动建表，应用能启动成功，所以**不会重启循环**
- 但第一次请求会报错，需要等 MySQL 初始化完成后手动重试

**怎么确保表初始化完成再启动 backend：**

1. **改进 MySQL 健康检查**：
   ```yaml
   healthcheck:
     test: ["CMD", "mysqladmin", "ping", "-h", "localhost", "-uroot", "-proot"]
     # 改成：先 ping，再检查某个表是否存在
     test: ["CMD-SHELL", "mysql -uroot -proot -e 'SELECT 1 FROM db_takeout.t_user LIMIT 1'"]
     interval: 5s
     timeout: 5s
     retries: 10
   ```

2. **应用层重试**：
   - Spring Boot 配置数据库连接失败重试
   - 或者用 `spring-boot-docker-compose` 模块，它能智能等待依赖就绪

3. **启动脚本等待**：
   - 在 backend 的 entrypoint 脚本里加一个 wait-for-it 脚本，等 MySQL 端口 + 等表存在

**当前项目的实际情况：**
- init.sql 不大（几个表+少量测试数据），初始化很快（几秒）
- 所以大概率不会出问题（backend 启动时 MySQL 已经初始化完了）
- 但如果 SQL 脚本很大（几百万条数据），就一定会碰到这个问题

---

### 93. docker-compose.yml 使用了 init/sql 挂载，确保首次启动时执行 DDL/DML。但 init.sql 中使用了 ALTER TABLE ADD COLUMN IF NOT EXISTS 的模拟（通过 information_schema.COLUMNS 检查）来幂等迁移。如果后续修改了 init.sql（如新增表或字段），因为 MySQL 容器使用了持久化 volume，挂载的初始化脚本不会再次执行——这时应该怎么完成数据库迁移？项目是否有计划引入 Flyway/Liquibase？

**回答：**

**问题本质：**
- `docker-entrypoint-initdb.d` 的脚本**只在首次启动（数据库为空）时执行一次**
- 后续启动，哪怕 SQL 文件改了，也不会再执行
- 所以它只能用于"初始化"，不能用于"版本迁移"

**当前 init.sql 中的"幂等迁移"能解决问题吗：**
- 比如用 `information_schema.COLUMNS` 检查字段是否存在，不存在才 ADD
- 这确实是幂等的——重复执行不会报错
- **但问题是：脚本根本不会被第二次执行**，所以写了也白写

**怎么完成后续的数据库迁移：**

**方案一：手动执行 SQL（当前最可能的做法）**
- 修改了表结构后，开发人员手动连接 MySQL 执行 ALTER
- 优点：简单直接
- 缺点：容易忘、容易错、多人协作混乱、生产环境危险

**方案二：引入 Flyway / Liquibase（推荐）**
- 专业的数据库版本迁移工具
- SQL 脚本按版本号命名（`V1__init.sql`、`V2__add_column.sql`）
- 应用启动时自动执行未执行过的脚本
- 有一张 `flyway_schema_history` 表记录已执行的版本

**方案三：MyBatis-Plus 的 DBW 模块**
- MyBatis-Plus 也有数据库迁移功能（类似 Flyway）
- 如果项目已经深度使用 MyBatis-Plus，可以考虑

**Flyway vs Liquibase：**

| 特性 | Flyway | Liquibase |
|------|--------|-----------|
| 学习曲线 | 简单，直接写 SQL | 稍复杂，支持 XML/YAML/JSON/SQL |
| 灵活性 | SQL 直接写，最灵活 | 支持多种格式，跨数据库 |
| 社区活跃度 | 高 | 高 |
| 适合场景 | 项目只用一种数据库 | 需要跨数据库兼容 |

**项目是否有计划引入：**
- 从代码来看，**没有引入**的迹象（pom.xml 里没有 flyway/liquibase 依赖）
- init.sql 里的"幂等迁移"更像是一种"临时方案"
- 建议：项目如果要长期维护，尽早引入 Flyway，越晚引入越痛苦

**什么时候必须引入：**
- 项目上线后，有真实用户数据了
- 不能删库重建，必须增量迁移
- 多人协作，需要管理数据库变更

---

### 94. Dockerfile 的两阶段构建中，build 阶段使用 maven:3.9-eclipse-temurin-17（约 700MB），runtime 阶段使用 eclipse-temurin:17-jre-alpine（约 180MB）。mvn package -DskipTests -q 编译出的 jar 是否包含了所有依赖？spring-boot-maven-plugin 默认打包方式会将依赖打入 fat jar，但多模块项目时要注意什么？当前项目是单体，这块有没有踩过坑？

**回答：**

**jar 是否包含所有依赖：**
- 是的。`spring-boot-maven-plugin` 的 `repackage` 目标会生成 fat jar（可执行 jar）
- fat jar 里面包含了所有的依赖（BOOT-INF/lib/ 目录下）
- 所以 runtime 阶段只需要 JRE，不需要 Maven，也不需要额外下载依赖

**为什么用两阶段构建：**
- 第一阶段（build）：有 Maven + JDK，编译打包
- 第二阶段（runtime）：只有 JRE，运行 jar
- 最终镜像只有 JRE + jar，体积小（~180MB + jar 体积）
- 如果只用一个阶段，镜像里有 Maven、JDK、源码，会大很多（~700MB+）

**多模块项目需要注意什么：**

1. **模块依赖关系**：
   - 父 pom 要先 install，子模块才能找到父
   - 所以 Dockerfile 里要先 COPY 整个项目，再 `mvn package`
   - 不能只 COPY 一个模块

2. **构建顺序**：
   - Maven 会根据依赖关系自动确定构建顺序
   - 但如果 Docker 缓存分层不好，改一个模块会导致所有模块重新构建

3. **启动模块**：
   - 多模块项目中，只有一个模块是启动入口（有 main 方法）
   - Dockerfile 里要 COPY 正确的那个模块的 jar

4. **依赖缓存优化**：
   - 多模块的 pom.xml 很多，分层 COPY 麻烦
   - 可以先 COPY 所有 pom.xml，`mvn dependency:go-offline`，再 COPY 源码

**当前单体项目有没有踩过坑：**

从当前 Dockerfile 推断（两阶段构建、alpine JRE），可能踩过的坑：

1. **镜像体积太大**：
   - 早期可能用了 JDK 镜像而不是 JRE 镜像
   - 或者没做多阶段构建，把 Maven 也打进运行镜像了

2. **Alpine 兼容性问题**：
   - Alpine 用 musl libc，某些 Java 依赖（特别是 JNI 相关的）可能不兼容
   - 但纯 Java 项目一般没问题

3. **时区问题**：
   - Alpine 镜像默认没有时区数据，需要额外安装 tzdata
   - 或者 JVM 参数指定时区

4. **字体问题**：
   - 如果有生成图片、验证码等功能，Alpine 可能缺字体
   - 当前项目好像不需要

5. **spring-boot-maven-plugin 配置**：
   - 如果插件没配置对，可能生成的不是可执行 jar
   - 或者 main class 找不到

**验证 fat jar 是否正确：**
- `jar tf takeout-app.jar | grep BOOT-INF/lib` —— 看有没有依赖 jar
- `java -jar takeout-app.jar` —— 能直接启动就对了

---

## 二十、主启动类与自动配置（Application）

### 95. TakeoutApplication 是空的启动类（只有 @SpringBootApplication 和 main），没有任何 @Enable* 注解。@SpringBootApplication 包含了 @EnableAutoConfiguration、@SpringBootConfiguration、@ComponentScan。如果某个模块的配置类（如 MybatisPlusConfig、JacksonConfig）没有被 @ComponentScan 扫到（比如放在了 com.takeout.config 外部），会不会出现自动配置失败的情况？项目中有没有配置类放在扫描路径之外？

**回答：**

**@SpringBootApplication 的扫描范围：**
- 默认扫描启动类所在包及其子包
- `TakeoutApplication` 在 `com.takeout` 包下
- 所以 `com.takeout.config`、`com.takeout.auth`、`com.takeout.order` 等都会被扫描到
- `MybatisPlusConfig`、`JacksonConfig`、`WebMvcConfig` 都在 `com.takeout.config` 下，没问题

**如果配置类放在扫描路径之外会怎样：**
- 比如放在 `com.example.config` 下
- Spring 扫不到，这个配置类就不会生效
- 可能导致：Bean 找不到、配置不生效、功能异常

**怎么扩大扫描范围：**
```java
@SpringBootApplication(scanBasePackages = {"com.takeout", "com.example"})
```
或者：
```java
@SpringBootApplication
@ComponentScan(basePackages = {"com.takeout", "com.example"})
```

**项目中有没有配置类在扫描路径之外：**
- 从已读的代码来看，**没有**。所有配置类都在 `com.takeout.config` 下
- 所有业务类也都在 `com.takeout` 子包下
- 结构很规范

**为什么不需要额外的 @Enable* 注解：**
- `@EnableAutoConfiguration` 已经包含在 `@SpringBootApplication` 里了
- Spring Boot 的自动配置机制会根据依赖自动配置
  - 有 `spring-boot-starter-web` → 自动配置 Spring MVC
  - 有 `mybatis-plus-spring-boot3-starter` → 自动配置 MyBatis-Plus
  - 有 `spring-boot-starter-data-redis` → 自动配置 Redis
  - 有 `knife4j-openapi3` → 自动配置 Knife4j
- 所以启动类很干净，什么都不用加

**什么时候需要手动加 @Enable*：**
- `@EnableTransactionManagement`：Spring Boot 会自动配置，不需要手动加
- `@EnableScheduling`：如果要用 @Scheduled，需要手动加
- `@EnableAsync`：如果要用 @Async，需要手动加
- `@EnableCaching`：如果要用 @Cacheable，需要手动加
- `@EnableWebSocket`：如果要用 WebSocket，需要手动加

**当前项目缺少的 @Enable*：**
- 没有 `@EnableScheduling`：所以 @Scheduled 不会生效
- 没有 `@EnableAsync`：所以 @Async 不会生效
- 没有 `@EnableCaching`：所以 @Cacheable 不会生效
- 这些功能当前项目都没用到，所以没问题

---

### 96. 项目没有 @EnableTransactionManagement，Spring Boot 3.x 中这个注解是否还是必须的？@Transactional 在没有 @EnableTransactionManagement 时能否正常工作？你确认过吗？

**回答：**

**答案：不需要手动加 @EnableTransactionManagement**

**原因：**
- Spring Boot 的自动配置类 `TransactionAutoConfiguration` 会自动启用事务管理
- 只要类路径下有 `spring-tx` 依赖（通过 spring-boot-starter-jdbc 或 mybatis-plus 传递引入），自动配置就会生效
- 所以即使不加 `@EnableTransactionManagement`，`@Transactional` 也能正常工作

**这是从什么时候开始的：**
- Spring Boot 1.4+ 就开始自动配置事务管理了
- 到 Spring Boot 2.x、3.x 都是这样
- 很多人不知道，还习惯性地加 `@EnableTransactionManagement`

**那 @EnableTransactionManagement 还有用吗：**
- 有用，可以用来自定义事务管理的配置
- 比如指定 `mode = AdviceMode.ASPECTJ`、`order = Ordered.HIGHEST_PRECEDENCE` 等
- 但默认配置（proxy 模式）下，完全不需要手动加

**怎么验证 @Transactional 是否生效：**

1. **看日志**：
   - 启动时日志中是否有 `DataSourceTransactionManager` 相关的初始化日志
   - 方法执行时是否有创建事务、提交/回滚的日志（需要 DEBUG 级别）

2. **写个测试**：
   ```java
   // 一个方法，前半段插入数据，后半段抛异常
   // 如果事务生效，数据应该回滚，查不到
   // 如果事务不生效，数据会被插入
   ```

3. **断点调试**：
   - 在 `@Transactional` 方法入口打断点
   - 看调用栈里有没有 `TransactionInterceptor`
   - 有就是代理生效了

**当前项目的情况：**
- `mybatis-plus-spring-boot3-starter` 会传递引入 `spring-boot-starter-jdbc`
- `spring-boot-starter-jdbc` 会触发 `DataSourceTransactionManagerAutoConfiguration`
- 所以 `@Transactional` 是正常工作的
- 代码中大量使用 `@Transactional`（OrderService、CouponService 等），如果不生效早就出 bug 了

**一个容易混淆的点：**
- Spring Boot 自动配置的是 `DataSourceTransactionManager`（JDBC 事务）
- 如果用 JPA，会自动配置 `JpaTransactionManager`
- 如果用多数据源，可能需要手动配置事务管理器

---

### 97. 项目没有 @EnableScheduling、@EnableAsync，也没有任何 @Scheduled 或 @Async 方法。对于订单超时未支付自动取消、商家评分异步聚合、订单完成后的消息推送等常见场景，依赖什么机制来实现？还是说这些功能全部暂未实现？

**回答：**

**结论：这些功能全部暂未实现**

**证据：**
- 没有 `@EnableScheduling` → 定时任务不能用
- 没有 `@EnableAsync` → 异步方法不能用
- 没有消息队列（RocketMQ/RabbitMQ/Kafka）
- 搜索代码中没有 `@Scheduled`、`@Async` 的使用

**哪些应该有但还没做的功能：**

| 功能 | 实现方式 | 当前状态 |
|------|---------|---------|
| 订单超时未支付自动取消 | 定时任务 + 扫描待支付订单 | ❌ 未实现 |
| 商家评分聚合计算 | 定时任务 / 异步事件 | ❌ 未实现（merchant.score 字段存在但从不更新）|
| 订单完成后通知商家 | WebSocket / 消息推送 | ❌ 未实现 |
| 优惠券到期提醒 | 定时任务 + 短信/推送 | ❌ 未实现 |
| 商家营业数据统计 | 定时任务每日凌晨计算 | ❌ 未实现 |
| 缓存预热 | 启动时 / 定时刷新 | ❌ 未实现 |

**订单超时取消怎么实现（如果要做）：**

**方案一：定时任务扫表（最简单）**
- 每分钟扫描一次 `t_order` 表，找出 status=1（待支付）且 create_time > 15分钟前的订单
- 批量取消
- 优点：简单
- 缺点：有延迟（最多 1 分钟）、订单多了扫表慢

**方案二：延迟队列（更优雅）**
- 下单时往延迟队列里塞一条消息，15 分钟后到期
- 到期后检查订单状态，如果还是待支付就取消
- 可以用 Redis 的 ZSET 实现延迟队列，或者用 RocketMQ 延迟消息
- 优点：实时性好、不扫表
- 缺点：实现稍复杂

**方案三：Redis 过期监听**
- 下单时设置一个 Redis key，15 分钟过期
- 监听 key 过期事件，触发订单取消检查
- 优点：利用 Redis 特性
- 缺点：Redis 过期事件不可靠（可能延迟）

**商家评分怎么计算（如果要做）：**
- 新增评价后，异步重新计算该商家的平均分
- 或者每天凌晨批量计算所有商家的评分
- 当前 `merchant.score` 字段一直是初始值，没更新过

**为什么这些功能没做：**
- 可能是 MVP（最小可行产品）版本，先做核心流程
- 也可能是"从微服务改造为单体"时砍掉了非核心功能
- 项目规划书里有这些功能，但实际代码没实现

---

## 二十一、测试（Testing）

### 98. 项目 src/test 目录不存在（零测试覆盖）。如果现在要补测试，应该优先补哪些模块的测试？OrderService.submit() 方法（90 行，依赖 5 个 Service、跨越 Redis + MySQL）写单元测试需要 mock 多少个依赖？这种高度耦合的 Service 层是否要优先重构可测试性再写测试？

**回答：**

**优先补哪些模块的测试：**

1. **OrderService（最高优先级）**：
   - 订单是核心业务，出 bug 影响最大
   - 状态流转、库存扣减、优惠券核销逻辑复杂
   - 出问题可能导致资金损失、超卖

2. **DishService（高优先级）**：
   - 库存扣减逻辑复杂（Redis Lua + MySQL）
   - 并发场景容易出问题
   - 超卖是严重事故

3. **CouponService（中优先级）**：
   - 优惠券领取、核销、退款
   - 涉及金额，出错会有资损

4. **AuthService（中优先级）**：
   - 登录、Token 校验
   - 安全相关，出问题影响大

5. **MerchantService / UserService（低优先级）**：
   - 增删改查，逻辑简单
   - 出问题影响相对小

**OrderService.submit() 写单元测试需要 mock 多少依赖：**

看一下 OrderService 的依赖：
```java
private final OrderMapper orderMapper;
private final OrderItemMapper orderItemMapper;
private final DishService dishService;
private final CouponService couponService;
private final MerchantService merchantService;
private final CartService cartService;
private final UserAddressService userAddressService;
private final StringRedisTemplate stringRedisTemplate;
private final RedisTemplate<String, Object> redisTemplate;
private final SnowflakeIdUtil snowflakeIdUtil;
```

**至少 10 个依赖**，其中：
- Mapper 类：2 个（OrderMapper、OrderItemMapper）
- Service 类：5 个（Dish、Coupon、Merchant、Cart、UserAddress）
- Redis 相关：2 个（StringRedisTemplate、RedisTemplate）
- 工具类：1 个（SnowflakeIdUtil）

而且 DishService、CouponService 内部又有自己的依赖（它们的 Mapper、Redis 等）

**高度耦合的 Service 层，先重构还是先写测试：**

这是经典的"先有鸡还是先有蛋"问题：
- 不写测试 → 重构容易改出 bug
- 不重构 → 测试太难写

**建议策略：**

1. **先写集成测试**：
   - 不用 mock，直接连真实的 MySQL 和 Redis（测试环境）
   - 测试完整的业务流程（下单、取消、支付）
   - 先保证核心流程不回归
   - 集成测试写起来相对简单（不用 mock）

2. **再重构核心模块**：
   - 有集成测试兜底，可以放心重构
   - 把 OrderService 拆分成多个小 Service
   - 提取分布式锁、库存扣减等逻辑为独立组件

3. **最后补单元测试**：
   - 重构后，每个小 Service 的依赖少了，单元测试好写了
   - 重点覆盖边界条件、异常场景

**另一种思路：测试金字塔倒置**
- 通常建议：多单元测试，少集成测试
- 但对于烂代码，反过来更有效：先写集成测试保证功能，再逐步重构补单元测试

**需要的测试工具：**
- `@SpringBootTest`：集成测试
- `@MockBean`：Mock Spring 容器中的 Bean
- `Testcontainers`：启动 MySQL/Redis 容器做集成测试
- `Mockito`：单元测试 mock
- `JUnit 5`：测试框架（Spring Boot 3.x 默认）

---

### 99. 没有测试的情况下，每次重构或修复 Bug 后靠什么保证不引入回归？靠人工点页面？如果某个修改（如 DishService.checkAndDeduct() 的 Lua 脚本逻辑调整）破坏了原有的库存一致性，现有的排查手段能在上线前发现问题吗？

**回答：**

**当前靠什么保证不回归：**
- **人工测试**：开发自己点页面，测试人员手工测
- **代码审查**：Code Review 靠人眼发现问题
- **经验和直觉**："我觉得改这个不会影响别的"

**人工测试的问题：**

1. **覆盖不全**：
   - 一个下单操作有 N 种组合（不同菜品、不同数量、用券/不用券、地址不同...）
   - 人工不可能覆盖所有场景
   - 特别是边界条件（库存刚好为 0、优惠券刚好过期等）

2. **效率低下**：
   - 每次改完都要从头点一遍
   - 项目功能越多，回归测试越慢
   - 最后变成"只测我改的那一块"，其他不测

3. **并发问题测不出来**：
   - 库存超卖、重复下单这类并发问题
   - 人工操作根本复现不了
   - 必须用压测工具或并发测试代码

**Lua 脚本逻辑调整破坏了库存一致性，能发现吗：**

**场景：修改了 STOCK_DEDUCT_LUA 脚本，引入了一个 bug**

- **人工测试**：大概率发现不了。正常下单流程看起来没问题，只有并发下才会超卖
- **Code Review**：如果 Reviewer 很懂 Lua 和 Redis，可能发现。但大多数人不细看 Lua 脚本
- **上线前**：基本发现不了
- **上线后**：用户投诉"下单成功了但库存没扣"或者运营发现"超卖了"，才会发现
- **发现时间**：可能几小时，也可能几天，取决于超卖的程度

**为什么这种问题容易漏：**
1. Lua 脚本是字符串，编辑器没有语法检查
2. 并发问题需要特定条件才触发
3. 没有自动化测试，每次改完都没人验证
4. 库存一致性问题，不盯着数据看发现不了

**怎么改善：**

1. **单元测试 + 集成测试**：
   - 库存扣减写专门的测试，覆盖各种边界
   - 并发测试：起 100 个线程同时扣，看结果对不对

2. **灰度发布**：
   - 先放少量流量，观察库存数据
   - 有问题及时回滚

3. **监控告警**：
   - 监控 Redis 库存和 MySQL 库存的差值
   - 差值超过阈值告警

4. **Code Review 制度**：
   - 核心逻辑（Lua、事务、状态流转）必须双人 Review
   - 不能一个人说改就改

**当前项目的现状：**
- 零测试 + 靠人工 = 风险很高
- 核心的库存、订单、优惠券逻辑没有自动化测试保障
- 改代码时心理压力大，不敢随便重构

---

### 100. 如果要为 OrderService.cancel() 写单元测试，Redis 分布式锁的代码（setIfAbsent + delete）需要 mock RedisTemplate。这个锁逻辑和业务逻辑在同一个方法内紧耦合，没有抽象出 DistributedLock 工具类——这种耦合对测试的阻碍有多大？如果要提取一个 LockTemplate，拆分的接口怎么设计？

**回答：**

**对测试的阻碍有多大：**

**阻碍很大，具体体现在：**

1. **mock 起来很麻烦**：
   - `redisTemplate.opsForValue().setIfAbsent()` 要 mock 两层：opsForValue() 返回 ValueOperations，再 mock setIfAbsent()
   - `redisTemplate.delete(lockKey)` 也要 mock
   - 每次写测试都要重复写这些 mock 代码

2. **锁的行为难模拟**：
   - 要测试"获取锁成功"的场景 → mock setIfAbsent 返回 true
   - 要测试"获取锁失败"的场景 → mock setIfAbsent 返回 false
   - 要测试"锁释放成功/失败" → 还要 mock delete
   - 场景多了，mock 代码量爆炸

3. **测试关注点跑偏**：
   - 单元测试应该关注业务逻辑对不对
   - 结果大量代码在 mock 分布式锁的细节
   - 测试代码可读性差

4. **无法验证锁的正确性**：
   - 比如"锁是不是在 finally 里释放了"
   - mock 只能验证"有没有调用 delete"，验证不了"是不是在 finally 里调用"
   - 除非用 PowerMock 之类的重工具

**提取 LockTemplate 的接口设计：**

**方案一：简单的分布式锁工具类**

```java
public interface DistributedLock {
    boolean tryLock(String key, long timeout, TimeUnit unit);
    void unlock(String key);
    
    <T> T executeWithLock(String key, long timeout, TimeUnit unit, Supplier<T> supplier);
    void executeWithLock(String key, long timeout, TimeUnit unit, Runnable runnable);
}
```

优点：
- 接口简单，易于理解和使用
- 自动释放锁，不会忘记 unlock
- 测试时可以 mock 整个 DistributedLock 接口

**方案二：模板方法模式（LockTemplate）**

```java
public interface LockTemplate {
    <T> T execute(String lockKey, long leaseTime, TimeUnit unit, LockCallback<T> callback);
    
    interface LockCallback<T> {
        T doInLock() throws Exception;
    }
}
```

使用方式：
```java
return lockTemplate.execute("order:" + orderNo, 30, TimeUnit.SECONDS, () -> {
    // 业务逻辑
    return cancelOrder(orderNo);
});
```

优点：
- 完全隐藏锁的获取/释放细节
- 业务代码只关心逻辑
- 测试时 mock LockTemplate 即可，非常简洁

**方案三：注解驱动（更优雅）**

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedLock {
    String key();          // 锁的 key（支持 SpEL）
    long timeout() default 30;
    TimeUnit unit() default TimeUnit.SECONDS;
}
```

配合 AOP 切面实现，业务代码零侵入。

**推荐方案：**

当前项目阶段推荐**方案二（LockTemplate）**，原因：
1. 比方案一更简洁，业务代码不用写 try-finally
2. 比方案三（AOP注解）简单，不需要 SpEL 解析
3. 测试极其方便：mock LockTemplate 的 execute 方法，直接调用 callback 即可
4. 未来可以平滑升级到 Redisson 的 RLock

**提取后的测试代码对比：**

提取前（约 30 行 mock 代码）：
```java
@Mock RedisTemplate<String, Object> redisTemplate;
@Mock ValueOperations<String, Object> valueOperations;

when(redisTemplate.opsForValue()).thenReturn(valueOperations);
when(valueOperations.setIfAbsent(anyString(), any(), any())).thenReturn(true);
// 还要 mock delete...
```

提取后（约 3 行）：
```java
@Mock LockTemplate lockTemplate;

when(lockTemplate.execute(eq("order:123"), eq(30L), eq(TimeUnit.SECONDS), any()))
    .thenAnswer(invocation -> {
        LockTemplate.LockCallback<?> callback = invocation.getArgument(3);
        return callback.doInLock();
    });
```

测试代码量减少 80%+，而且关注点回到业务逻辑本身。

---

### 101. ResultCode 只有 7 个错误码，没有针对业务错误码的测试。如果团队多人协作，A 新加了一个 ORDER_TIMEOUT(601)，B 新加了一个 COUPON_EXPIRED(601)，两个 code 冲突了——现有的代码组织方式能发现吗？运行时会产生什么混淆？

**回答：**

**现有的代码组织方式能发现吗：**

- **编译时发现不了**：Java 编译器不会检查枚举值的 code 是否重复
- **运行时可能发现也可能发现不了**：
  - 如果只是 code 重复但 name 不同，代码能正常运行，不会报错
  - 只有在做反向映射（根据 code 找枚举）时可能出问题

**运行时会产生什么混淆：**

1. **前端判断混乱**：
   - 前端根据 code 做不同的 UI 处理（比如 601 是"订单超时"还是"优惠券过期"？）
   - 同一个 code 两种含义，前端无法区分
   - 用户可能看到错误的提示信息

2. **后端逻辑混乱**：
   - 如果有代码 `if (result.getCode() == 601) { ... }`，到底在处理哪种错误？
   - 日志排查困难，看到 code=601 不知道是什么错误

3. **最危险的情况：**
   - 如果 A 的 601 是"需要重试"的错误，B 的 601 是"不能重试"的错误
   - 前端根据 code 决定是否重试，可能导致严重 bug（如重复下单）

**怎么防止 code 冲突：**

**方案一：单元测试（最简单有效）**
```java
@Test
void testResultCodeUnique() {
    Map<Integer, ResultCode> map = new HashMap<>();
    for (ResultCode code : ResultCode.values()) {
        assertNull("code 重复: " + code.getCode(), 
                   map.put(code.getCode(), code));
    }
}
```
- 跑一次测试就能发现所有重复 code
- 集成到 CI，每次构建自动检查

**方案二：按模块分段（约定大于配置）**
```
4xx: 通用错误（400 参数错误、401 未登录、403 无权限、404 不存在）
5xx: 系统错误（500 系统异常）
600-699: 订单模块
700-799: 优惠券模块
800-899: 用户模块
900-999: 商家模块
```
- 每个模块分配一个号段，减少冲突概率
- 看到 code 就知道是哪个模块的错误

**方案三：静态代码检查**
- 用 SonarQube 自定义规则检查枚举值唯一性
- 或者用 ArchUnit 做架构测试

**推荐：方案一 + 方案二结合**
- 按模块分段是约定，单元测试是强制执行
- 双重保险，成本极低

---

## 二十二、日志与监控（Logging & Monitoring）

### 102. 项目没有 logback-spring.xml 配置，使用 Spring Boot 默认的日志配置。生产环境下默认的日志是滚动切割的吗？如果不配置 logback-spring.xml，日志文件会无限增长吗？logging.level.com.takeout: DEBUG 在线上会产生多少日志量？每笔订单从提交到完成会打出多少条 DEBUG 日志？

**回答：**

**Spring Boot 默认的日志轮转策略：**

**控制台日志（Console）：**
- 默认只输出到控制台，不轮转
- Docker 环境下控制台日志由 Docker 引擎管理（默认有轮转）

**文件日志（File）：**
- 如果配置了 `logging.file.name` 或 `logging.file.path`，会输出到文件
- **默认轮转策略**（Spring Boot 默认 Logback 配置）：
  - 单个文件最大 10MB
  - 最多保留 7 个归档文件
  - 归档文件名：`app.log.2024-01-01.0.gz`（按日期 + 索引）
  - 总文件大小上限：无限制（只限制文件数）

**日志文件会无限增长吗：**
- **不会无限增长**，因为有按大小切割 + 按天数保留
- 但**磁盘占用可能越来越大**，因为默认只限制文件数量不限制总大小
- 如果日志量很大，7 个文件可能每个都 10MB，总共 70MB（可控）
- 但如果没有配置文件输出（只有控制台），Docker 的日志驱动可能会无限增长（取决于 Docker 配置）

**DEBUG 级别在线上的日志量：**

以一笔订单从提交到完成为例，估算 DEBUG 日志条数：

| 阶段 | 操作 | DEBUG 日志数（估算） |
|------|------|---------------------|
| 提交订单 | Controller 入参、Service 调用 | 5-10 条 |
| 库存扣减 | Redis Lua 执行、MySQL 更新 | 10-20 条 |
| 订单创建 | MyBatis SQL、参数、结果 | 15-30 条 |
| 清购物车 | DELETE SQL | 3-5 条 |
| 优惠券核销 | 查询、更新 | 5-10 条 |
| 支付回调 | 状态校验、更新 | 10-15 条 |
| **合计** | | **约 50-100 条/订单** |

如果每天 1 万单，DEBUG 级别日志量：
- 10,000 × 80 条 = 80 万条/天
- 每条约 200 字节 → 约 160MB/天
- 加上其他接口（商家列表、用户信息等），可能 300-500MB/天
- 7 天就是 2-3.5GB，磁盘压力不小

**更严重的问题：**

1. **MyBatis 的 DEBUG 日志**：
   - 会打印完整的 SQL 语句、参数、结果集
   - 一个复杂查询可能打出几百行日志
   - 包含敏感数据（用户手机号、地址等）

2. **性能影响**：
   - DEBUG 日志量大，写磁盘 IO 开销大
   - 可能拖慢响应时间 10%-30%

3. **排查困难**：
   - 日志太多，找 ERROR 像大海捞针

**生产环境建议：**
- 日志级别：`INFO`（默认）或 `WARN`
- 错误日志：`ERROR` 级别单独输出到 error 文件
- 关键业务日志：用 `INFO` 级别手动输出（如下单、支付、退款）
- 调试需要时：通过动态日志级别临时打开（Spring Boot Actuator 的 loggers 端点）

---

### 103. GlobalExceptionHandler.handleException(Exception.class) 打印了 log.error("系统异常: {}", e.getMessage(), e)，但异常堆栈只在 ERROR 级别输出。如果某个 BusinessException 被当做 Exception 兜底捕获了（因为类型是 RuntimeException），会错误地走到 handleException 的字符串匹配分支吗？BusinessException 的 handleBusiness 匹配在 Exception 之前还是之后？确定过 Spring 的异常匹配优先级吗？

**回答：**

**Spring 的异常匹配优先级：**

Spring MVC 的 `@ExceptionHandler` 匹配规则是：**找最具体的异常类型匹配**

具体规则：
1. 优先匹配**精确的异常类型**（如 BusinessException）
2. 如果找不到，匹配**父类异常类型**（如 RuntimeException）
3. 如果还找不到，匹配**最顶层的 Exception**
4. 同一层级的异常，按方法定义顺序？不，是按继承关系的近远

**结论：BusinessException 会被 handleBusiness 捕获，不会走到 handleException**

因为 `BusinessException` 是 `RuntimeException` 的子类，`RuntimeException` 又是 `Exception` 的子类：
- `handleBusiness(BusinessException e)` → 最匹配，优先调用
- `handleException(Exception e)` → 最不匹配，最后兜底

**验证方式：**
- 看 GlobalExceptionHandler 的方法定义顺序不影响匹配结果
- Spring 内部用 `ExceptionHandlerMethodResolver` 解析，会找"最接近的异常类型"
- 可以理解为"继承树中最近的那个"

**那什么情况下会走到 handleException：**
- 不是 BusinessException 的其他 RuntimeException（如 NullPointerException、IllegalArgumentException）
- 受检异常（Checked Exception）
- Error（如果配置了的话）

**字符串匹配分支会误判吗：**

```java
@ExceptionHandler(Exception.class)
public Result<Void> handleException(Exception e) {
    String msg = e.getMessage();
    if (msg != null && msg.contains("MySQL")) {
        return Result.fail(FAIL, "数据库连接失败");
    }
    // ...
}
```

- BusinessException 不会走到这里，所以不会被误判
- 但其他异常如果 message 刚好包含 "MySQL" 字样，会被误判
- 比如："用户 MySQL 账号不存在"（业务异常，但如果不是 BusinessException 的话）

**更严重的问题：异常嵌套**

```java
// 真正的异常可能是 cause
throw new RuntimeException("业务处理失败", 
    new DataAccessException("MySQL connection refused"));
```

- 这时候 `e.getMessage()` 是 "业务处理失败"，不包含 "MySQL"
- 真正的 MySQL 异常在 cause 里
- 所以字符串匹配会漏掉这种情况，返回通用错误而不是数据库错误提示

**正确的异常分类做法：**
1. 按**异常类型**匹配，不是按 message 字符串
2. 用 `@ExceptionHandler(DataAccessException.class)` 专门处理数据库异常
3. 用 `@ExceptionHandler(RedisConnectionFailureException.class)` 处理 Redis 异常
4. Exception 兜底只做通用处理，不做字符串猜测

---

### 104. 健康检查接口 /api/health 返回自定义的 Map 结构，而不是 Spring Boot Actuator 的标准 Health 对象。如果后续要集成到 K8s 的 livenessProbe / readinessProbe 或 Spring Boot Admin，自定义返回格式需要额外适配。为什么不用 spring-boot-starter-actuator？不使用 Actuator 之后，你失去了哪些开箱即用的端点（metrics、info、env、heapdump）？

**回答：**

**为什么不用 Actuator（推测）：**

1. **项目初期觉得不需要**：小项目，健康检查自己写一个就行
2. **不知道 Actuator 的强大**：以为只有健康检查，不知道还有 metrics、监控等功能
3. **安全考虑**：怕 Actuator 端点泄露敏感信息（其实可以配置管理）
4. **从微服务改造时砍掉了**：单体了就不需要监控了？

**失去了哪些开箱即用的端点：**

| 端点 | 作用 | 价值 |
|------|------|------|
| `/actuator/health` | 健康检查 | 标准格式，K8s/SBA 直接支持 |
| `/actuator/metrics` | 应用指标 | JVM、CPU、内存、线程池、HTTP 请求等 |
| `/actuator/info` | 应用信息 | 版本、构建时间、Git 信息等 |
| `/actuator/env` | 环境变量 | 查看配置（可脱敏） |
| `/actuator/beans` | Bean 列表 | 调试 Spring 容器问题 |
| `/actuator/loggers` | 日志级别管理 | 运行时动态修改日志级别，不用重启 |
| `/actuator/heapdump` | 堆转储 | OOM 时 dump 堆内存分析 |
| `/actuator/threaddump` | 线程转储 | 排查死锁、CPU 飙高 |
| `/actuator/prometheus` | Prometheus 格式指标 | 接入 Prometheus + Grafana 监控 |
| `/actuator/scheduledtasks` | 定时任务列表 | 查看所有 @Scheduled 任务 |
| `/actuator/caches` | 缓存管理器 | 查看缓存统计、手动清理缓存 |

**最有价值的几个端点（按实用程度排序）：**

1. **metrics + prometheus**：
   - 不用写一行代码就能拿到 JVM、HTTP、Tomcat、数据源等指标
   - 接入 Grafana 就能有完整的监控大盘
   - 自定义指标也很简单（MeterRegistry）

2. **health**：
   - 标准格式，支持多个指标（db、redis、disk、ping）
   - K8s 直接用来做 liveness/readiness 探针
   - 可以自定义健康指标（如 Kafka 连接状态）

3. **loggers**：
   - 生产环境出问题时，临时打开某个包的 DEBUG 日志
   - 排查完再改回 INFO
   - 不用重启应用，对排查线上问题帮助极大

4. **heapdump + threaddump**：
   - 线上 OOM、CPU 飙高时，直接 dump 分析
   - 不需要登录服务器执行 jmap/jstack

**为什么应该引入 Actuator：**

- **开发成本几乎为零**：加一个依赖就行
- **安全性可控**：
  - `management.endpoints.web.exposure.include=health,info,prometheus`
  - 敏感端点（env、heapdump）可以不暴露或加安全认证
  - 管理端口可以单独配置（`management.server.port=8081`）
- **生态完善**：K8s、Spring Boot Admin、Prometheus、Grafana 都直接支持
- **生产环境必备**：没有监控的系统就是"裸奔"

**当前自定义 HealthController 的问题：**
- 只检查了 MySQL 和 Redis 的连通性
- 没有检查磁盘空间、JVM 内存、线程池状态等
- 非标准格式，对接监控系统需要额外适配
- 没有聚合状态（多个检查项怎么汇总为 UP/DOWN）

建议：尽快引入 `spring-boot-starter-actuator`，至少暴露 health、info、prometheus 三个端点。

---

## 二十三、DTO/VO 设计与数据转换

### 105. 整个项目的数据转换全是手动 toVO() / toDTO() 方法，没有使用 MapStruct 或 BeanUtils。这种模式在字段少的时候可读性好，但如果有 30 个字段的 VO，手写 get/set 的缺点是什么？BeanUtils.copyProperties() 相比于 MapStruct 的性能劣势是多少（反射 vs 编译期生成）？

**回答：**

**手写 get/set 的缺点：**

1. **代码量大，重复劳动**：
   - 30 个字段的 VO，toVO 方法要写 30 行 get/set
   - 每个 VO 都要写一遍，枯燥乏味
   - 容易漏写某个字段（复制粘贴时漏了一个）

2. **容易出错**：
   - 字段名相似时容易写错（如 `setUserName(user.getUsername())`）
   - 类型不匹配时（Long → String）需要手动转，容易忘
   - 新增字段时容易忘记加到 toVO 里

3. **维护成本高**：
   - Entity 加了字段，要同时改 N 个 toVO 方法
   - 字段改名时，要改 N 处（IDE 重构能帮点忙，但不是 100% 可靠）
   - 代码审查时要看一大堆 get/set，浪费时间

4. **可读性差**：
   - 30 行 get/set 摆在那，一眼看过去都是重复代码
   - 真正的业务逻辑被淹没在样板代码中

**BeanUtils.copyProperties 的问题：**

| 特性 | BeanUtils（Spring） | MapStruct |
|------|---------------------|----------|
| 实现方式 | 反射（运行时） | 代码生成（编译期） |
| 性能 | 慢（10-100 倍差距） | 快（和手写差不多） |
| 类型安全 | 运行时才发现类型不匹配 | 编译期报错 |
| 字段映射 | 靠名字匹配，写错了运行时才发现 | 编译期检查，显式配置 |
| 嵌套映射 | 不支持（需要手动处理） | 支持 |
| 自定义转换 | 麻烦（需要 Converter） | 方便（@Mapping 注解） |
| 调试难度 | 反射调用，断点难打 | 生成的 Java 代码，可直接看 |

**性能差距有多大：**

- 简单对象（10 个字段）：BeanUtils 比 MapStruct 慢 **5-10 倍**
- 复杂对象（嵌套、集合）：慢 **20-100 倍**
- 绝对时间：
  - MapStruct：每次转换约 0.01-0.1 微秒
  - BeanUtils：每次转换约 0.1-1 微秒
  - 手写：每次转换约 0.01-0.05 微秒

**为什么 BeanUtils 慢：**
- 反射要做方法查找、权限检查、参数装箱拆箱
- 每次都要内省（Introspector.getBeanInfo）
- 虽然有缓存，但还是比直接调用慢

**什么时候用 BeanUtils 没问题：**
- 低频率调用（管理后台接口，QPS < 10）
- 对象简单（几个字段）
- 性能不是瓶颈

**手写 vs MapStruct 怎么选：**

| 场景 | 推荐方案 | 理由 |
|------|---------|------|
| 字段少（< 5个） | 手写 | 简单直接，依赖少 |
| 字段多（> 10个） | MapStruct | 减少样板代码，减少错误 |
| 嵌套对象、集合 | MapStruct | 手写太麻烦 |
| 性能要求高 | MapStruct / 手写 | BeanUtils 太慢 |
| 类型转换复杂 | MapStruct | 灵活的 @Mapping 配置 |

**当前项目的情况：**
- 大多数 VO 字段不多（5-15 个），手写还能接受
- 但 OrderVO、MerchantVO 字段不少，手写容易出问题
- 建议：核心模块（订单、商品）引入 MapStruct，简单模块保持手写
- 渐进式引入，不需要一次性全改

---

### 106. OrderService.toVO() 中每次调用都 selectList(...) 查询 OrderItem 列表。如果一个页面列出 20 个订单（每个订单有 3-5 个商品），listMyOrders() 会执行 1 次分页查询 + 20 次 OrderItem 子查询。这是典型的 N+1 问题吗？为什么不用 LEFT JOIN 或 MyBatis Plus 的 @TableName(autoResultMap=true) + @TableField(exist=false) + 关联查询？

**回答：**

**这是典型的 N+1 问题吗：**

- **是的，这是典型的 N+1 查询问题**
- 1 次主查询（订单列表） + N 次子查询（每个订单的商品明细） = N+1 次查询
- N=20 时，就是 21 次数据库查询
- 如果 N=100，就是 101 次，性能会很差

**为什么会有 N+1 问题：**

- MyBatis-Plus 的 `BaseMapper` 只做单表 CRUD，不支持关联查询
- 开发者图方便，直接在循环里查明细
- 没有意识到 N+1 的性能影响（数据量小时感觉不出来）

**为什么不用 LEFT JOIN：**

推测原因：
1. **MyBatis-Plus 的使用习惯**：习惯了用 BaseMapper 单表操作，写 XML 觉得麻烦
2. **分页处理复杂**：LEFT JOIN 后，一条订单对应多条明细，分页会有问题（LIMIT 限制的是行数不是订单数）
3. **结果映射麻烦**：需要手动处理一对多的结果映射（ResultMap + collection）
4. **数据量小**：每个订单商品不多，觉得 N+1 也还好

**MyBatis-Plus 的关联查询方案：**

**方案一：XML + ResultMap（最经典）**
```xml
<resultMap id="OrderWithItems" type="OrderVO">
    <id property="id" column="id"/>
    <result property="orderNo" column="order_no"/>
    <!-- ... 其他字段 ... -->
    <collection property="items" ofType="OrderItemVO">
        <id property="id" column="item_id"/>
        <result property="dishName" column="dish_name"/>
    </collection>
</resultMap>
```
- SQL 用 LEFT JOIN，一次查询搞定
- 分页要用子查询（先分页查订单 ID，再 JOIN 明细）

**方案二：两次查询 + 手动组装（折中方案）**
```java
// 1. 查订单列表
Page<Order> orderPage = orderMapper.selectPage(page, wrapper);
// 2. 查所有订单的明细（一次 IN 查询）
List<OrderItem> items = orderItemMapper.selectList(
    new LambdaQueryWrapper<OrderItem>()
        .in(OrderItem::getOrderId, orderIds)
);
// 3. 手动按 orderId 分组，组装到每个 OrderVO 里
Map<Long, List<OrderItem>> itemsMap = items.stream()
    .collect(Collectors.groupingBy(OrderItem::getOrderId));
```
- 只有 2 次查询（1 + 1，不是 N+1）
- 分页简单，单表查询性能好
- 代码也不复杂，容易理解

**方案三：MyBatis-Plus 的 @TableField(exist = false)**
- 在 Entity 上加 `@TableField(exist = false)` 字段表示非数据库字段
- 然后自定义 XML 做关联查询，结果映射到 Entity
- 但 Entity 里掺杂了非数据库字段，不太纯净

**推荐方案：方案二（两次查询 + 手动组装）**

理由：
1. **性能好**：从 N+1 降到 2 次查询
2. **实现简单**：不用写复杂的 XML ResultMap
3. **分页简单**：单表分页，MyBatis-Plus 原生支持
4. **灵活**：可以控制哪些字段需要查，不需要全量查

对于大多数场景，方案二是性价比最高的，代码量增加不多，性能提升明显。

**当前场景下 N+1 的影响有多大：**
- 20 个订单 × 每个 3-5 个商品：21 次查询 vs 2 次查询
- 单次查询 1ms 的话，N+1 是 21ms，优化后是 2ms
- 看起来差距不大，但数据库连接是稀缺资源，21 次查询会占用更多连接时间
- 并发高了之后，连接池容易耗尽
- 而且 N 越大差距越明显（如果每页 50 条、100 条）

---

### 107. DishSnapshotVO 使用了 record 类型，而 MerchantVO 用的是 class。这种"同一模块内 record 和 class 混用"是渐进式重构的结果还是有意设计？record 的不可变性在某些场景下（如 Jackson 反序列化、AOP 代理）是否有限制？有没有遇到过 record 不能被动态代理的情况？

**回答：**

**混用的原因：**

推测是**渐进式重构或不同开发者习惯**：
- `DishSnapshotVO` 是后来写的，用了 Java 16+ 的新特性 record
- `MerchantVO` 是早期写的，用的传统 class
- 没有统一的规范，导致模块内风格不一致
- 不太可能是"有意设计"，因为 record 和 class 的选用不应该随机

**record 的不可变性带来的限制：**

**1. Jackson 反序列化：**
- 完全支持，没有问题
- Jackson 2.12+ 原生支持 Java record
- 反序列化时通过构造器创建对象（因为是 final 的）
- `@JsonProperty`、`@JsonAlias` 等注解可以加在构造器参数上
- 需要注意：record 的组件名要和 JSON 字段名一致（或用 @JsonProperty 指定）

**2. AOP 动态代理：**
- **record 不能被 JDK 动态代理**：因为 JDK 动态代理要求目标类实现接口，record 虽然也能实现接口，但通常 VO/DTO 不实现接口
- **record 不能被 CGLIB 代理**：CGLIB 通过生成子类来代理，而 record 是 final 的，不能被继承
- **结论：record 类不能被 Spring AOP 代理**

但这对 VO/DTO 来说是**问题吗？**
- 通常不是问题，因为 VO/DTO 就是数据载体，不需要被 AOP 代理
- Service 层的 Bean 才需要 AOP（事务、日志等），而 Service 是 class 不是 record

**3. 其他限制：**

| 特性 | class | record |
|------|-------|--------|
| 继承其他类 | 可以 | 不行（隐式继承 java.lang.Record） |
| 被继承 | 可以 | 不行（final 类） |
| 非 final 字段 | 可以 | 不行（所有组件都是 final） |
| 可变（setter） | 可以 | 不行（没有 setter） |
| 额外实例字段 | 可以 | 不行（只能有构造器参数对应的组件） |
| 实现接口 | 可以 | 可以 |
| 静态字段/方法 | 可以 | 可以 |

**什么时候该用 record：**

- DTO / VO / 值对象（纯数据载体）
- 字段少，不需要修改
- 需要值相等语义（equals/hashCode 按所有字段比较）
- 希望类是不可变的（线程安全）

**什么时候不该用 record：**

- 需要 setter 方法（但 record 可以加 with 方法返回新对象）
- 需要继承父类
- 有很多业务方法（但可以在 record 里定义实例方法）
- 需要被 CGLIB 代理（但 VO 不需要）

**当前项目的建议：**
- VO/DTO 这种纯数据传输对象，用 record 是合适的
- 但应该统一风格，要么都用 record，要么都用 class
- 如果团队熟悉 record，建议逐步把简单的 VO 都改成 record
- 注意：有嵌套对象、需要自定义序列化的要谨慎评估

---

## 二十四、枚举与常量管理

### 108. 项目中唯一存在的枚举是 UserRole.java（CUSTOMER、ADMIN、MERCHANT），但订单状态、商家状态、用户状态全部用魔法数字硬编码在 Service 和 Controller 中。如果看到一个「把数字改成常量」的提交要求——你会建议新增常量类、枚举，还是用什么方式？团队怎么保证后续开发不再引入新的魔法数字？

**回答：**

**推荐方案：用枚举（Enum），不是常量类**

| 方案 | 优点 | 缺点 |
|------|------|------|
| 常量类（public static final int） | 简单，直接用数字比较 | 没有类型安全，int 可以传任意值，没有方法和行为 |
| 枚举（Enum） | 类型安全、有方法、可遍历、可 switch | 稍"重"一点（但可忽略） |

**为什么选枚举：**

1. **类型安全**：
   ```java
   // 常量类：可以传任意 int，编译不报错
   public void updateStatus(int status) { ... }
   
   // 枚举：只能传枚举值，传错编译报错
   public void updateStatus(OrderStatus status) { ... }
   ```

2. **有行为和属性**：
   ```java
   public enum OrderStatus {
       PENDING_PAYMENT(1, "待支付", true),
       PENDING_ACCEPT(2, "待接单", true),
       // ...
       
       private final int code;
       private final String desc;
       private final boolean canCancel;  // 能不能取消
       
       public boolean canTransitionTo(OrderStatus target) { ... }
   }
   ```
   - 状态描述、状态流转逻辑都可以封装在枚举里
   - 比散落在各处的 if-else 好维护

3. **可遍历**：
   - `OrderStatus.values()` 拿到所有状态
   - 前端下拉框、管理后台筛选可以直接用

4. **可 switch**：
   - switch 表达式配合枚举，代码更清晰

**枚举的设计规范：**

```java
@Getter
@AllArgsConstructor
public enum OrderStatus {
    PENDING_PAYMENT(1, "待支付"),
    PENDING_ACCEPT(2, "待接单"),
    PREPARING(3, "备餐中"),
    DELIVERING(5, "配送中"),
    COMPLETED(6, "已完成"),
    CANCELLED(7, "已取消");
    
    private final int code;
    private final String desc;
    
    // 根据 code 找枚举
    public static OrderStatus of(int code) {
        for (OrderStatus s : values()) {
            if (s.code == code) return s;
        }
        throw new IllegalArgumentException("未知状态: " + code);
    }
    
    // 判断能否流转到目标状态
    public boolean canTransitionTo(OrderStatus target) {
        // 状态机逻辑
        return switch (this) {
            case PENDING_PAYMENT -> target == PENDING_ACCEPT || target == CANCELLED;
            case PENDING_ACCEPT -> target == PREPARING || target == CANCELLED;
            // ...
            default -> false;
        };
    }
}
```

**怎么防止再引入魔法数字：**

1. **静态代码检查（最有效）**：
   - SonarQube 规则：Magic Number 检测
   - 阿里巴巴 Java 开发手册插件：检测魔法数字
   - CI 集成，不通过不让合并

2. **代码审查（Code Review）**：
   - 看到魔法数字直接打回
   - 形成团队共识

3. **架构测试（ArchUnit）**：
   ```java
   @Test
   void noMagicNumbers() {
       // 检查 Service 层代码中没有直接使用数字字面量（除了 0、1 等常见值）
       // 配合 ArchUnit 或自定义规则
   }
   ```

4. **IDE 提示**：
   - IDEA  inspections 开启 Magic Number 检查
   - 开发时就能看到警告

**过渡策略：**
- 不要一次性把所有魔法数字都改成枚举，改动太大容易出 bug
- 按模块逐步改造，先改核心模块（订单状态）
- 新代码必须用枚举，老代码逐步迁移
- 每次改完跑一遍测试，验证状态流转正确

---

### 109. OrderService 中所有状态流转都是 updateStatusWithLock(order, expected, newStatus, reason) 调用。如果未来新加一个"配送异常"状态（status=8），需要在多少处地方同步修改：OrderService 流转方法 × N、submit() 中的状态校验 × 1、cancel() 中的状态判断 × 1、前端状态显示组件 × 3（H5 + 商家 + 管理台）？最容易被遗漏的是哪几处？

**回答：**

**需要修改的地方（估算）：**

**后端（Java）：**

| 位置 | 数量 | 说明 |
|------|------|------|
| OrderService.submit() | 1 | 初始状态设置、校验 |
| OrderService.cancel() | 1 | 哪些状态可以取消 |
| OrderService.payOrder() | 1 | 待支付 → 待接单 |
| OrderService.accept() / reject() | 1 | 待接单 → 备餐中 / 取消 |
| OrderService.ready() | 1 | 备餐中 → 配送中 |
| OrderService.complete() / receive() | 1 | 配送中 → 已完成 |
| OrderService.listMyOrders() | 1 | 状态筛选条件 |
| OrderService.listMerchantOrders() | 1 | 商家端状态筛选 |
| OrderService.listAdminOrders() | 1 | 管理台状态筛选 |
| OrderController / OrderMerchantController | 2-3 | 参数校验、状态判断 |
| **后端小计** | **约 10-15 处** | |

**前端（3 个项目）：**

| 位置 | 数量 | 说明 |
|------|------|------|
| 订单列表状态显示 | 3 | 每个前端一个 |
| 订单详情状态显示 | 3 | 每个前端一个 |
| 状态流转按钮显示 | 3-6 | 不同状态显示不同按钮 |
| 状态筛选下拉框 | 3 | 筛选订单用 |
| 订单状态枚举文件 | 3 | 前端的状态常量 |
| **前端小计** | **约 15-20 处** | |

**总计：约 25-35 处**，而且还不包括：
- 数据库注释（COMMENT）
- API 文档（Knife4j 注解）
- 业务逻辑文档
- 测试用例

**最容易被遗漏的几处：**

1. **逆向判断逻辑**：
   - "哪些状态可以取消"、"哪些状态可以评价"
   - 大家都关注"新增状态后要改哪些流转"，但容易忘了"这个新状态能不能参与其他操作"
   - 比如：配送异常状态的订单能不能取消？能不能评价？

2. **列表筛选条件**：
   - "待处理订单"的筛选条件（可能是 2+3+5 等多个状态的组合）
   - 新增状态后，要不要加到某个筛选分组里？
   - 管理台的各种状态 tab

3. **统计/报表 SQL**：
   - "今日完成订单数"、"营业额"等统计 SQL 中的 status 条件
   - 这些 SQL 往往藏在 XML 或 mapper 里，容易忘改

4. **定时任务**：
   - 订单超时取消（只扫待支付状态）
   - 自动确认收货（只扫配送中状态）
   - 新增状态后，定时任务要不要也处理这个状态？

5. **前端的默认处理**：
   - 前端如果有 `default: '未知状态'` 的兜底，可能不会报错
   - 但显示"未知状态"用户体验差
   - 而且按钮显示逻辑可能不对（比如配送异常状态也显示"确认收货"按钮）

**怎么减少遗漏：**

1. **统一枚举管理**：
   - 后端枚举 + 前端枚举，一处定义多处使用
   - 甚至可以用代码生成工具，后端枚举自动生成前端 TypeScript 类型

2. **状态机模式**：
   - 把所有状态流转逻辑集中在一个类/枚举里
   - 新增状态只需要改一个地方（状态机配置）
   - 而不是散落在 N 个方法里

3. **全链路搜索**：
   - 改之前先全局搜 `status == 1`、`status = 1`、`eq(Status, 1)` 等
   - 把所有涉及状态判断的地方都列出来
   - 逐个确认是否需要改

4. **测试用例覆盖**：
   - 每个状态流转都有对应的测试用例
   - 新增状态后跑一遍所有测试，看看哪里失败了

**根本解法：状态机模式**
- 把状态和流转关系集中配置
- 新增状态只需要加配置，不需要改业务代码
- 可以用 Spring Statemachine 或自己实现简单的状态机

---

### 110. UserRole 枚举使用了 name() 作为序列化方式，Jackson 序列化后是小写还是大写？前端传递的 role 值是 ADMIN、admin 还是 "admin"？如果是前端传小写，valueOf() 会直接抛错（IllegalArgumentException），现有代码用 try-catch 吞掉了，但 CUSTOMER 是不安全的默认值——如果传了 ADMINI（多了一个 I），用户会被降级为 CUSTOMER 吗？权限会受损吗？

**回答：**

**Jackson 序列化后的大小写：**

- 默认情况下，Jackson 序列化枚举用的是 `name()` 方法
- `UserRole.ADMIN.name()` 返回 `"ADMIN"`（大写）
- 所以序列化后是 **大写** 的：`{"role": "ADMIN"}`

- 如果配置了 `@JsonValue` 或自定义序列化器，可能不同
- 但从代码看，UserRole 枚举没有这些注解，所以是默认的 name()

**前端传小写会怎样：**

- `UserRole.valueOf("admin")` 会抛 `IllegalArgumentException`
- 因为 valueOf 是**大小写敏感**的，必须和枚举名完全一致
- 现有代码的处理方式：
  ```java
  try {
      role = UserRole.valueOf(roleStr);
  } catch (Exception e) {
      role = UserRole.CUSTOMER; // 默认值
  }
  ```
- 传小写的话，会被降级为 CUSTOMER

**传了 ADMINI（多了一个 I）会怎样：**

- **会被降级为 CUSTOMER**
- 因为 `valueOf("ADMINI")` 找不到对应的枚举值，抛异常
- catch 块捕获后，设置为默认值 CUSTOMER
- 用户权限从管理员降级为普通顾客

**这会导致什么问题：**

1. **权限降级（越往下越安全，但体验差）**：
   - 管理员输入错误 → 变成普通用户 → 看不到管理后台 → 以为自己账号出问题了
   - 商家输入错误 → 变成普通用户 → 看不到商家后台
   - 虽然不会越权（往下降级），但用户体验很差

2. **拼写错误难以发现**：
   - 前端写错了一个字母，后端静默降级
   - 前端开发者可能不知道自己写错了，以为就是这个权限
   - 调试困难

3. **安全隐患（如果默认值是更高权限就惨了）**：
   - 还好这里默认是 CUSTOMER（最低权限），是安全的
   - 如果默认是 ADMIN，那就是严重的越权漏洞
   - 但即使是降级，也是不正确的行为

**更好的处理方式：**

**方案一：明确报错，前端处理（推荐）**
```java
try {
    role = UserRole.valueOf(roleStr.toUpperCase());
} catch (IllegalArgumentException e) {
    throw new BusinessException(ResultCode.PARAM_ERROR, "无效的角色类型: " + roleStr);
}
```
- 传错了直接告诉前端"角色无效"
- 前端根据错误码提示用户
- 不静默降级，问题早发现

**方案二：大小写不敏感**
```java
public static UserRole of(String name) {
    for (UserRole role : values()) {
        if (role.name().equalsIgnoreCase(name)) {
            return role;
        }
    }
    throw new IllegalArgumentException("无效角色: " + name);
}
```
- 前端传 admin、Admin、ADMIN 都能识别
- 用户体验更好

**方案三：用 code 而不是 name**
```java
public enum UserRole {
    CUSTOMER(1, "顾客"),
    MERCHANT(2, "商家"),
    ADMIN(3, "管理员");
    
    private final int code;
    // ...
}
```
- 前后端用数字 code 传递，而不是枚举名
- 不会有大小写问题
- 但可读性差一些

**前端应该传什么：**
- 理想情况：前端传的 role 值和后端枚举名完全一致（大写）
- 实际情况：前端可能传小写（JavaScript 习惯用小写）
- 建议：后端兼容大小写（toUpperCase 或自定义 of 方法），降低对接成本

**当前代码的安全性：**
- 默认值 CUSTOMER 是安全的（权限最低）
- 不会导致越权
- 但会导致"权限降级"的 bug，用户体验差
- 建议改成明确报错，而不是静默降级

---

## 二十五、ID 生成策略

### 111. SnowflakeIdUtil 使用固定参数 new SnowflakeIdUtil(1, 1) 创建单例，workerId 和 dataCenterId 都是 1。单体部署时固定值没问题，但如果未来拆回微服务（5 个节点），每个服务实例的 workerId 怎么分配？手动配置还是自动发现？如果两个服务实例的 workerId 相同，生成的 ID 会重复吗？

**回答：**

**两个实例 workerId 相同会重复吗：**

- **在同一毫秒内会重复**
- 雪花算法的结构：`时间戳 + dataCenterId + workerId + 序列号`
- 如果 dataCenterId 和 workerId 都相同，同一毫秒内的序列号也相同，生成的 ID 就完全一样
- 不同毫秒的话，时间戳不同，ID 还是不同的
- 所以：**高并发下会重复，低并发下可能侥幸不重复**

**workerId 怎么分配（5 个节点）：**

**方案一：手动配置（简单但麻烦）**
- 每个实例的配置文件里手动指定 `worker.id=1`、`worker.id=2` ...
- 优点：简单，不依赖额外组件
- 缺点：
  - 部署麻烦，每个实例配置不一样
  - 扩容时要记得分配新的 workerId
  - 容易配重复（人工操作难免出错）
  - 容器化部署（K8s）时不方便

**方案二：基于 IP 地址计算（半自动）**
- 用 IP 地址的最后一段对 32 取模作为 workerId
- 比如 IP 是 192.168.1.105 → 105 % 32 = 9 → workerId=9
- 优点：不用手动配置，启动时自动计算
- 缺点：
  - IP 最后两位可能重复（不同网段）
  - 机器数超过 32 会冲突
  - 容器环境 IP 可能动态变化

**方案三：Redis 分配（推荐，动态分配）**
- 服务启动时，去 Redis 里申请一个 workerId
- 用 Redis 的 INCR 或 SETNX 分配
- 配合心跳续约，实例挂了自动回收
- 优点：自动分配，不用人工管
- 缺点：依赖 Redis（但项目已经有 Redis 了）

**方案四：ZooKeeper / Nacos 注册中心分配**
- 如果有注册中心，可以用节点序号作为 workerId
- 或者用 ZK 的持久有序节点分配
- 优点：和服务发现结合
- 缺点：依赖重

**方案五：雪花算法变种（百度 UidGenerator、美团 Leaf）**
- 成熟的开源方案，解决了 workerId 分配、时钟回拨等问题
- 优点：生产级，可靠性高
- 缺点：引入新组件，有学习成本

**拆回微服务后的分配建议：**

如果是 5 个节点的小规模：
- **短期**：手动配置，每个服务实例配不同的 workerId
- **长期**：用 Redis 自动分配，或引入 Leaf/MeiLeaf

如果是 5 个服务 × 每个服务 3 个实例 = 15 个实例：
- dataCenterId 可以按服务类型划分（订单服务=1，用户服务=2...）
- workerId 按实例序号（0, 1, 2）
- 这样 5×32=160 个实例，完全够用

**当前代码的问题：**
- workerId 和 dataCenterId 硬编码为 1
- 没有配置化，也没有自动分配
- 单体没问题，但一扩容就有问题
- 建议：至少改成配置文件注入（`${snowflake.worker-id:1}`），为以后留口子

---

### 112. 雪花算法代码中，System.currentTimeMillis() 获取的服务器时间一旦发生时钟回拨，会抛出 RuntimeException("时钟回拨，拒绝生成 ID")。但这个异常没有被 GlobalExceptionHandler 捕获（不是 BusinessException），会直接返回 500 给前端。如果时钟回拨了 1 秒，系统在回拨期间不能生成任何订单号——这个设计在真实线上环境可以接受吗？有没有更好的处理方式？

**回答：**

**这个设计可以接受吗：**

**取决于时钟回拨的频率和时长：**

- **NTP 同步导致的毫秒级回拨**：常见，每次同步可能回拨几毫秒到几十毫秒
  - 这个频率下，用户偶尔下单失败，体验还能接受
  - 但如果每秒都有几次回拨，就不行了

- **人为误操作导致的秒级回拨**：少见，但影响大
  - 回拨 1 秒，这 1 秒内所有生成 ID 的请求都失败
  - 下单、支付、评价都用不了，影响很大

- **更严重的情况：回拨后时间追上之前**
  - 比如回拨了 5 秒，需要等 5 秒才能恢复
  - 这 5 秒内系统"残废"了

**结论：对于外卖系统，不能接受**
- 外卖是交易系统，下单失败 = 营收损失
- 即使只是几秒的不可用，也可能影响很多订单
- 需要更友好的处理方式

**更好的处理方式：**

**方案一：等待时钟追上（简单但有停顿）**
```java
if (timestamp < lastTimestamp) {
    long offset = lastTimestamp - timestamp;
    if (offset <= 5) { // 回拨小于 5ms，等待
        Thread.sleep(offset);
        timestamp = timeGen();
        if (timestamp < lastTimestamp) {
            throw new RuntimeException("时钟回拨");
        }
    } else {
        throw new RuntimeException("时钟回拨过大");
    }
}
```
- 回拨时间短就等一下，长了再报错
- 适合 NTP 同步导致的小幅度回拨

**方案二：用备用 workerId（美团 Leaf 方案）**
- 每个实例准备多个 workerId（比如 0-9）
- 时钟回拨时，切换到下一个 workerId
- 只要不是所有 workerId 都回拨过，就能继续生成
- 优点：几乎无停顿
- 缺点：workerId 消耗快，需要有回收机制

**方案三：序列号偏移（简单有效）**
- 时钟回拨但差距不大时，可以继续用 lastTimestamp + 序列号
- 相当于用序列号来"借"时间
- 但序列号用完了还是得等
- 适合回拨时间短、QPS 不高的场景

**方案四：容忍一定范围的回拨 + 告警**
- 回拨在 1 秒内的话，用 lastTimestamp 继续生成（序列号用完就等）
- 同时发出告警，通知运维
- 超过阈值（如 2 秒）才拒绝服务
- 兼顾可用性和安全性

**行业内的成熟方案对比：**

| 方案 | 代表项目 | 原理 | 可用性 |
|------|---------|------|--------|
| 直接报错 | 简单实现 | 回拨就抛异常 | 差 |
| 等待重试 | 很多手写实现 | sleep 等时间追上 | 中 |
| 备用 workerId | 美团 Leaf | 换一个 workerId 继续 | 好 |
| 时间序列扩展 | 百度 UidGenerator | 借位，用未来时间 | 好 |
| 完全避免时钟依赖 | UUID / 数据库号段 | 不依赖时间 | 最好 |

**推荐方案：**

对于当前项目（中小型）：
1. **小回拨等待**：回拨 < 50ms 就 sleep 等待
2. **中回拨告警 + 继续**：回拨 < 1s，用 lastTimestamp + 序列号（序列号不够就等），同时告警
3. **大回拨报错**：回拨 > 1s，拒绝服务 + 告警
4. **workerId 配置化**：至少可以手动配置

对于大型项目：
- 直接用成熟方案（美团 Leaf、百度 UidGenerator）
- 不要自己造轮子

**当前代码的问题：**
- 直接抛 RuntimeException，没有任何降级策略
- 异常类型不是 BusinessException，前端看到的是"系统异常"而不是友好提示
- 没有告警机制，出问题了运维可能不知道
- 建议至少优化为：小回拨等待 + 大回拨报错 + 友好错误信息

---

### 113. Order 实体使用 IdType.ASSIGN_ID（雪花 ID），而订单号 orderNo 又单独使用 SnowflakeIdUtil.generate() 生成——也就是说一条订单记录有 id 和 orderNo 两个雪花 ID。为什么需要两个雪花 ID？直接使用 id 作为订单号会有什么问题？

**回答：**

**为什么需要两个雪花 ID（推测）：**

1. **业务字段 vs 技术字段分离**：
   - `id` 是数据库主键（技术字段），用于表关联、索引
   - `orderNo` 是业务订单号（业务字段），给用户看、给外部系统用
   - 分开的话，以后如果订单号规则变了（比如加前缀、加日期），不影响数据库主键

2. **订单号可能有业务含义**：
   - 有些系统的订单号包含业务信息（如时间、商家 ID、类型）
   - 比如 `2024010112345678`（日期 + 序号）
   - 但当前项目 orderNo 也是纯雪花 ID，看不出业务含义

3. **安全性考虑**：
   - 数据库自增 ID 是连续的，容易被遍历（/order/1, /order/2, ...）
   - 雪花 ID 是乱序的，相对安全
   - 但如果 id 和 orderNo 都是雪花 ID，这个理由不成立

4. **历史原因（从微服务改造过来）**：
   - 微服务架构下，订单号可能是由专门的 ID 生成服务生成的
   - 数据库 id 是各服务自己的自增或雪花
   - 改单体后保留了两个 ID 的设计

5. **对接外部系统**：
   - 支付、配送等外部系统用的是 orderNo
   - 内部数据库关联用 id
   - 分开的话，外部系统不用知道内部主键

**直接用 id 作为订单号会有什么问题：**

**如果 id 和 orderNo 都是雪花 ID，功能上其实没问题**，但有以下隐患：

1. **未来订单号规则可能变化**：
   - 比如运营说"订单号前面加个商家编码"或"加个日期前缀"
   - 如果直接用 id 作为订单号，改起来就麻烦了（因为 id 是主键，不能改）
   - 而 orderNo 是业务字段，可以随便改格式

2. **数据库主键和业务字段耦合**：
   - 主键应该是无意义的技术字段
   - 订单号是有业务含义的（虽然现在没有，但以后可能有）
   - 耦合在一起不符合数据库设计规范

3. **外部系统集成风险**：
   - 如果外部系统（支付、配送）存了订单号
   - 以后你想改订单号格式就难了（外部系统不答应）
   - 而 orderNo 独立的话，至少数据库主键不用动

4. **分库分表考虑**：
   - 以后订单量大了要分库分表
   - 主键 id 可能要用来做分片键
   - 订单号可能有不同的路由规则
   - 分开更灵活

**当前项目的实际情况：**

从代码看：
- `id` 和 `orderNo` 都是雪花 ID，值不同（不同的 SnowflakeIdUtil 实例？不，应该是同一个）
- 等等，如果是同一个单例，同一毫秒内的两个调用，序列号不同，ID 也不同
- 所以 id 和 orderNo 是两个不同的雪花 ID

这两个字段都是雪花 ID，功能重复了。但**保留 orderNo 是更具前瞻性的设计**，因为：
- 订单号是暴露给用户和外部的
- 内部主键是技术实现，不应该暴露
- 以后要改订单号格式的话，只改 orderNo 就行，不影响主键

**建议：**
- 保留两个 ID 的设计（虽然现在看起来多余）
- 但可以考虑让 orderNo 有一定的业务格式（如日期 + 雪花 ID）
- 或者至少加个前缀，让用户能区分订单号和其他 ID

---

## 二十六、配置管理（Configuration）

### 114. application.yml 中 JWT secret 硬编码为 takeout-system-jwt-secret-key-2024-very-long，看起来是测试密钥，但密码要求至少 32 字节，这个字符串正好 44 个字符。如果代码通过 Git 公开，攻击者可以用这个 secret 签发任意角色的 Token。有没有考虑过使用环境变量 ${JWT_SECRET} 或外部配置中心？为什么没有？

**回答：**

**为什么没有用环境变量或配置中心（推测）：**

1. **图方便**：开发时直接写在 yml 里，不用配置环境变量
2. **安全意识不足**：没意识到代码仓库可能泄露，觉得"项目是私有的没事"
3. **项目初期**：先跑起来再说，安全配置以后再调
4. **不知道怎么弄**：不熟悉 Spring Boot 的环境变量注入、配置中心等

**硬编码的风险：**

1. **代码仓库泄露 → 密钥泄露**：
   - 即使是私有仓库，也可能因为配置错误、员工离职、黑客入侵等原因泄露
   - 开源项目更不用说了
   - 历史版本中也有密钥（即使你后来删了，git history 里还在）

2. **多环境问题**：
   - 开发、测试、生产用同一个密钥？
   - 还是每个环境在代码里写不同的？（不可能，因为代码是同一份）
   - 正确做法是不同环境不同密钥，通过环境变量注入

3. **密钥轮换困难**：
   - 要换密钥就得改代码、重新发布
   - 不能动态切换
   - 万一密钥泄露了，不能紧急更换（要等发版）

**正确的做法：**

**方案一：环境变量（最简单）**
```yaml
jwt:
  secret: ${JWT_SECRET:default-dev-secret-key-change-me}
```
- 生产环境通过环境变量 `JWT_SECRET` 注入真实密钥
- 开发环境有默认值（方便本地开发）
- 默认值要明确标注是"开发用，生产请修改"

**方案二：配置中心（更专业）**
- Nacos / Apollo / Spring Cloud Config
- 密钥存在配置中心，加密存储
- 可以动态刷新，不用重启应用
- 适合多服务、多环境的场景

**方案三：加密配置（Jasypt）**
```yaml
jwt:
  secret: ENC(加密后的密文)
```
- 用 jasypt-spring-boot-starter
- 配置文件里是密文，运行时解密
- 但加密密钥本身又存在哪？（又回到了密钥管理的问题）

**方案四：KMS / 密钥管理服务**
- 阿里云 KMS、AWS KMS、HashiCorp Vault
- 密钥由专业的密钥管理服务管理
- 应用启动时从 KMS 拉取密钥
- 最安全，但成本最高

**当前项目的建议：**
- 短期：用**环境变量**方式，成本最低，见效最快
- 中期：如果引入 Nacos 等配置中心，放到配置中心
- 长期：业务做大了考虑 KMS

**额外的安全建议：**
1. **密钥定期轮换**：3-6 个月换一次
2. **多密钥支持**：支持新旧密钥同时验证（平滑过渡）
3. **最小权限**：知道生产密钥的人越少越好
4. **代码仓库扫描**：用 git hook 或工具扫描，防止密钥提交到代码库

---

### 115. 项目有两个 profile：默认（application.yml）和 docker（application-docker.yml）。spring.profiles.active 通过环境变量 SPRING_PROFILES_ACTIVE=docker 传入，但 application.yml 中配置了 spring.datasource.password: root。如果生产环境改用不同密码，是通过覆盖 profile 中的 password、环境变量、还是 jasypt 加密？当前方案支持以上哪种？

**回答：**

**Spring Boot 配置的优先级（从高到低）：**

1. 命令行参数（`--spring.datasource.password=xxx`）
2. 环境变量（`SPRING_DATASOURCE_PASSWORD=xxx`）
3. `application-{profile}.yml`（当前激活的 profile）
4. `application.yml`（默认配置）
5. `@PropertySource` 注解

**优先级记住一句话：** 环境变量 > 配置文件（profile > 默认）

**当前方案支持哪些密码配置方式：**

| 方式 | 是否支持 | 说明 |
|------|---------|------|
| 覆盖 docker profile 中的 password | ✅ 支持 | 在 application-docker.yml 里加 `spring.datasource.password: 生产密码` |
| 环境变量 | ✅ 支持 | 传 `SPRING_DATASOURCE_PASSWORD=xxx` 环境变量 |
| Jasypt 加密 | ❌ 不支持 | 没有引入 jasypt 依赖 |

**推荐方式：环境变量**

原因：
1. **最安全**：密码不会出现在代码仓库、配置文件里
2. **最灵活**：每个环境密码不一样，不用改代码
3. **符合云原生最佳实践**：12-Factor App 推荐用环境变量管理配置
4. **Docker/K8s 友好**：通过 Secret 或 ConfigMap 注入环境变量

**具体怎么做：**

**Docker 方式：**
```yaml
# docker-compose.yml
services:
  backend:
    image: takeout-backend
    environment:
      - SPRING_DATASOURCE_PASSWORD=${DB_PASSWORD}  # 从宿主机环境变量取
      - SPRING_DATA_REDIS_PASSWORD=${REDIS_PASSWORD}
      - JWT_SECRET=${JWT_SECRET}
```
- 宿主机上设置环境变量，docker-compose 传递进去
- 或者用 `.env` 文件

**K8s 方式：**
```yaml
env:
  - name: SPRING_DATASOURCE_PASSWORD
    valueFrom:
      secretKeyRef:
        name: db-secret
        key: password
```
- 用 K8s Secret 管理敏感配置
- 更安全，有 RBAC 权限控制

**为什么不推荐放 application-docker.yml：**
- 密码会出现在代码仓库里（不安全）
- 不同环境需要不同的 profile 文件，麻烦
- 改密码要改代码、重新发版（不灵活）

**当前配置的问题：**
- `application.yml` 里明文写了 `password: root`
- 如果代码泄露，数据库密码就泄露了
- 建议：
  1. 默认值改成空或 `changeme`，提醒生产环境必须配置
  2. 生产环境通过环境变量注入真实密码
  3. 敏感配置（密码、密钥）一律走环境变量

---

### 116. application.yml 中 spring.data.redis.lettuce.pool.max-active: 8，但 orderService.submit() 方法中同时使用 Redis 做分布式锁和库存扣减，高峰期 100 并发下单请求同时到来，8 个连接池会耗尽吗？Lettuce 连接池耗尽时会阻塞还是抛出异常？超时时间在哪里配置的？

**回答：**

**先澄清一个概念：Lettuce 的连接模型**

Lettuce 是**基于 Netty 的异步非阻塞**客户端，和 Jedis 的连接池模型不一样：
- Jedis：每个请求借一个连接，用完归还，连接池大小 = 最大并发数
- Lettuce：**单个连接可以处理多个并发请求**（因为是异步 + 多路复用）
- Lettuce 的连接池是"连接数"，不是"并发数"

所以 Lettuce 的 `max-active=8` 和 Jedis 的 `max-active=8` 含义不同：
- Jedis 的 8：最多 8 个并发请求（第 9 个要等）
- Lettuce 的 8：最多 8 个 Redis 连接，每个连接可以处理 N 个并发请求

**8 个连接够不够：**

对于 100 并发下单请求：
- 每个下单请求大概需要 3-5 次 Redis 操作（锁、库存 Lua、优惠券、购物车...）
- 100 并发 × 5 次 = 500 次 Redis 操作
- 但 Lettuce 单个连接就能处理几百个并发请求（异步 + 队列）
- 所以 **8 个连接完全够用**，甚至 1 个连接都能扛住

**什么时候会不够：**
- 大量阻塞命令（如 `BLPOP`）会占用连接
- 事务（`MULTI/EXEC`）需要独占连接
- Pub/Sub 需要单独的连接
- 这些场景下连接池才容易耗尽

**连接池耗尽时会怎样：**

**Lettuce 默认行为：阻塞等待**
- 连接池满了之后，新的请求会等待连接释放
- 等待超时时间：`max-wait`（默认 -1，即无限等待？不对，默认通常是 -1 表示立即失败或有限等待，要看具体配置）
- 实际上 Lettuce 的 `GenericObjectPool` 配置的 `maxWaitMillis` 默认是 -1（无限等待），但 Spring Boot 的默认值可能不同

需要查 Spring Boot 的默认配置：
- `spring.data.redis.lettuce.pool.max-wait` 默认是 -1ms（无限等待）
- 但实际应用不可能无限等，会超时

**更详细的 Lettuce 连接池配置：**

| 配置 | 默认值 | 说明 |
|------|--------|------|
| max-active | 8 | 最大连接数 |
| max-idle | 8 | 最大空闲连接数 |
| min-idle | 0 | 最小空闲连接数 |
| max-wait | -1ms | 等待连接的最大时间，-1 表示无限等待 |

**建议配置：**
```yaml
spring:
  data:
    redis:
      lettuce:
        pool:
          max-active: 16       # 适当调大
          max-idle: 8
          min-idle: 2
          max-wait: 1000ms     # 最多等 1 秒，超时就报错
```

**超时了会抛什么异常：**
- `RedisConnectionFailureException` 或 `PoolException`
- 包装成 Spring 的 `DataAccessException` 体系
- 最终被 GlobalExceptionHandler 的字符串匹配（"Redis"）捕获，返回"Redis 连接失败"

**100 并发下单的实际压力估算：**

假设：
- 每个下单请求有 5 次 Redis 操作
- 每次 Redis 操作耗时 1ms（本地 Redis）
- 100 并发 → 500 次 Redis 操作/秒
- 单连接 QPS 能到几千到几万
- 8 个连接完全够用，不会耗尽

**真正可能的瓶颈：**
- 不是连接数不够
- 而是 Redis 本身的 QPS 上限（单节点 10 万 QPS 左右）
- 或者 Lua 脚本执行时间过长（阻塞其他命令）

**当前项目的风险：**
- `max-wait` 可能是默认的 -1（无限等待）
- 如果 Redis 挂了，所有请求都卡在那里等连接
- 导致线程池耗尽，整个服务雪崩
- 建议设置合理的 `max-wait`（如 1-3 秒），快速失败而不是无限等待

---

## 二十七、前端架构与后端关系

### 117. 项目有三个独立前端工程（H5、merchant-web、admin-web），但后端是一个单体。三个前端工程共享同一个认证接口 /api/auth/login，但各自有不同的前端路由和权限页面。如果某个接口（如 GET /api/merchant/nearby）需要新增一个查询参数，修改链路是后端改 1 处 + 前端改 1 处还是 3 处？如果三个前端对同一个 API 的使用方式不一致，这是谁的责任？

**回答：**

**新增参数需要改几处：**

**取决于这个参数三个前端是否都需要：**

| 场景 | 后端修改 | 前端修改 | 总改动 |
|------|---------|---------|--------|
| 三个前端都用这个接口且都需要新参数 | 1 处 | 3 处（H5 + 商家 + 管理台）| 4 处 |
| 只有 H5 需要（比如附近商家） | 1 处 | 1 处（H5） | 2 处 |
| 参数是可选的，不传不影响 | 1 处 | 0 处（兼容旧版） | 1 处 |

对于 `GET /api/merchant/nearby` 这个接口：
- H5 端用（用户端附近商家）
- 商家端和管理台可能不用
- 所以大概率是后端 1 处 + 前端 1 处（H5）

**但实际情况可能更复杂：**
- 接口改了，API 文档要更（Knife4j 注解）
- 类型定义要改（如果有 TypeScript 类型定义）
- 测试用例要改

**三个前端对同一个 API 使用方式不一致，谁的责任：**

**这是典型的"前后端协作"问题，责任在双方：**

**后端的责任：**
1. **API 设计不清晰**：
   - 参数含义、返回值结构、错误码没有明确的文档
   - 三个前端各自理解，各用各的
   - 后端应该提供明确的 API 契约（OpenAPI、TypeScript 类型定义）

2. **没有统一的 SDK/客户端**：
   - 如果后端能生成前端 SDK（根据 OpenAPI 生成 TypeScript 客户端）
   - 三个前端都用同一个 SDK，就不会不一致

3. **版本管理混乱**：
   - API 变更没有通知所有前端团队
   - 或者通知了，但各前端改的时间不一样

**前端的责任：**
1. **没有封装统一的 API 层**：
   - 每个前端自己写请求代码
   - 同一个接口，参数名、响应处理都可能不一样
   - 应该抽出一个公共的 API 包（如 `@takeout/api-client`），三个前端共用

2. **不看文档，自己猜**：
   - 不看后端的 Knife4j 文档
   - 自己看着返回值写类型定义
   - 容易理解错

**正确的协作模式：**

1. **API 优先（API-First）**：
   - 先定义 API 契约（OpenAPI YAML）
   - 前后端都按契约开发
   - 从契约生成前端 TypeScript 类型和后端接口

2. **共享类型定义**：
   - 后端的 OpenAPI 文档 → 生成前端 TypeScript 接口
   - 三个前端共用生成的类型
   - 不会出现"你理解的字段名和我理解的不一样"

3. **统一的 API 客户端**：
   - 抽出一个 npm 包 `@takeout/api`
   - 封装所有请求，三个前端都用它
   - 接口改了，只改这个包，三个前端升级版本就行

**当前项目的情况：**
- 三个前端独立工程，没有共享 API 层
- 后端用 Knife4j 生成文档，但前端可能不看
- 很容易出现不一致
- 建议：至少把 API 类型定义抽成公共包，三个前端共用

---

### 118. 前端访问后端时，CORS 配置使用 allowedOriginPatterns("*") 允许所有来源，这在开发环境很方便。但三个前端分别运行在 localhost:3001（H5）、localhost:3002（商家）、localhost:3003（管理台），是否应该针对这三个具体域名做 CORS 白名单以达到最小的安全攻击面？生产环境下，这三个前端会部署到不同域名还是同一域名（子域名）？

**回答：**

**开发环境是否应该用白名单：**

**开发环境可以宽松，但最好也是白名单**，原因：
1. **攻击面小**：只允许三个 localhost 端口，其他来源不能跨域调用
2. **提前发现问题**：如果加了新的前端项目但忘了配 CORS，开发时就会发现
3. **习惯养成**：开发环境就用白名单，生产环境不会忘

但开发环境用 `*` 也能接受，因为：
- 本地开发，没有安全风险
- 方便（加新前端不用改后端配置）
- 很多项目都是这么做的

**生产环境的部署方式：**

**两种常见方案：**

**方案一：三个子域名（最常见）**
```
h5.takeout.com          # 用户端 H5
merchant.takeout.com    # 商家端
admin.takeout.com       # 管理台
api.takeout.com         # 后端 API
```
- 三个前端三个域名，后端一个独立域名
- CORS 需要配置三个白名单
- 优点：部署独立，互不影响
- 缺点：需要四个域名，CORS 配置稍麻烦

**方案二：同域名不同路径（需要网关/反向代理）**
```
www.takeout.com/h5          # 用户端 H5
www.takeout.com/merchant    # 商家端
www.takeout.com/admin       # 管理台
www.takeout.com/api         # 后端 API
```
- 同一个域名，用路径区分
- 不需要 CORS（同源）
- 需要 Nginx 或网关做路径转发
- 优点：没有跨域问题，一个域名搞定
- 缺点：部署耦合，一个挂了可能影响其他的（可以通过路径转发解决）

**推荐方案：生产环境用子域名 + CORS 白名单**

理由：
1. 三个端是不同的产品，独立部署更灵活
2. 一个端升级不会影响其他端
3. 安全隔离更好（cookie 可以按子域名隔离）

**CORS 白名单配置建议：**

```yaml
cors:
  allowed-origins:
    - https://h5.takeout.com
    - https://merchant.takeout.com
    - https://admin.takeout.com
  # 开发环境
    - http://localhost:3001
    - http://localhost:3002
    - http://localhost:3003
```

或者按环境分：
- `application-dev.yml`：允许 localhost:3001/3002/3003
- `application-prod.yml`：允许三个生产域名

**更安全的做法：**
1. **生产环境收窄 CORS**：只允许三个正式域名
2. **Cookie 的 SameSite**：设置 `SameSite=Lax` 或 `Strict`，防止 CSRF
3. **不要用 `allowCredentials(true)` + `allowedOrigins("*")`**：浏览器会拒绝
4. **敏感操作加 CSRF Token**：即使 CORS 配置了，敏感操作也要加 CSRF 防护

**当前配置的风险：**
- `allowedOriginPatterns("*")` + `allowCredentials(true)` 是非常宽松的配置
- 任何网站都能跨域调用你的接口（只要用户登录了）
- CSRF 攻击的风险很大
- 生产环境必须收窄白名单

---

## 二十八、数据建模与字段设计

### 119. t_merchant.score 使用 DECIMAL(3,1)，字面意义最大值为 99.9。但评分系统的逻辑最大值是 5.0，DB 层允许存入 99.9，如果代码 Bug 导致 avgScoreByMerchant 计算异常时传入了 100 分，DB 会截断还是报错？DECIMAL(2,1) 最大只能存 9.9，为什么不用 DECIMAL(2,1) 或者直接用 TINYINT（0-50 表示 0.0-5.0）？

**回答：**

**传入 100 分 DB 会怎样：**

**`DECIMAL(3,1)` 的含义：**
- 总位数（精度）：3 位
- 小数位数：1 位
- 整数部分：3 - 1 = 2 位
- 取值范围：`-99.9 ~ 99.9`

**如果传入 100.0：**
- 100.0 的整数部分是 3 位，超过了 2 位
- MySQL 会**报错**，而不是截断
- 错误：`Out of range value for column 'score' at row 1`
- 不会静默截断，会直接抛异常

**如果传入 99.9：**
- 刚好是最大值，能存进去
- 但这不符合业务逻辑（评分最高 5.0）

**为什么不用 DECIMAL(2,1)：**

DECIMAL(2,1) 的范围是 -9.9 ~ 9.9，对 5.0 满分来说完全够用。

推测没用的原因：
1. **设计时预留了余量**：万一以后评分改成 10 分制呢？
2. **复制粘贴**：其他地方用了 DECIMAL(3,1)，直接搬过来
3. **没想那么多**：觉得 3 和 2 差别不大，无所谓

**用 TINYINT 存 0-50 表示 0.0-5.0 呢：**

| 方案 | 优点 | 缺点 |
|------|------|------|
| DECIMAL(2,1) | 直观，直接存 4.5 | 存储空间稍大（3 字节），计算稍慢 |
| TINYINT | 存储空间小（1 字节），计算快 | 不直观，50 表示 5.0 需要转换 |

**对于评分字段：**
- 评分是展示用的，计算量不大
- 用 DECIMAL 更直观，代码可读性好
- 不需要为了省 2 个字节用 TINYINT
- 所以推荐 `DECIMAL(2,1)` 而不是 TINYINT

**但更大的问题是：数据库层没有做业务约束**

`DECIMAL(3,1)` 允许 0-99.9，但业务上只允许 0-5.0。
- 如果代码有 bug，写入了 10 分、50 分，数据库也会接受
- 直到用户看到"评分 99.9"才会发现

**建议增加的约束：**

1. **数据库层 CHECK 约束**（MySQL 8.0.16+）：
   ```sql
   ALTER TABLE t_merchant ADD CONSTRAINT check_score CHECK (score >= 0 AND score <= 5);
   ```
   - 数据库层面保证数据合法性
   - 代码 bug 也不会写入非法值

2. **应用层校验**：
   - 更新评分前校验范围
   - 双重保险

3. **字段类型优化**：
   - 从 `DECIMAL(3,1)` 改成 `DECIMAL(2,1)`
   - 更紧凑，也能起到一定的约束作用（至少存不了 10 分以上）

**当前项目的实际情况：**
- 用了 `DECIMAL(3,1)`，但 merchant.score 几乎没更新过（第 46 题分析过）
- 所以这个问题目前是"潜在风险"，还没触发
- 建议：有时间就改成 DECIMAL(2,1) + CHECK 约束，没时间就先放着（影响不大）

---

### 120. t_order 表的 longitude 和 latitude 使用 DECIMAL 类型，精度不明。MyBatis-Plus 映射为 BigDecimal。但 Merchant 同样有经纬度，使用的是 BigDecimal。如果后续要基于地理坐标做距离排序（ST_Distance_Sphere），DECIMAL 的精度和索引设计是否满足？为什么没有在 longitude 和 latitude 上加复合索引？

**回答：**

**DECIMAL 精度够吗：**

**经纬度需要多少精度：**
- 经度范围：-180 ~ 180
- 纬度范围：-90 ~ 90
- 精度要求：
  - 小数点后 4 位：约 11 米精度
  - 小数点后 5 位：约 1.1 米精度
  - 小数点后 6 位：约 0.11 米精度

**外卖系统需要多少精度：**
- 外卖配送，精度到"米"级就够了
- 小数点后 6 位完全够用
- 所以 `DECIMAL(10,6)` 或 `DECIMAL(9,6)` 比较合适

**当前 DECIMAL 的精度：**
- 题目说"精度不明"——需要看具体建表 SQL
- 如果是 `DECIMAL(10,6)` 就合适
- 如果精度太低（如只有 2 位小数），距离计算会有误差

**MySQL 的 ST_Distance_Sphere 对类型有要求吗：**
- `ST_Distance_Sphere()` 接受 POINT 类型参数
- POINT 内部用 DOUBLE 存储坐标
- 所以 DECIMAL 传进去会被转成 DOUBLE
- 精度上 DECIMAL 转 DOUBLE 没问题（DOUBLE 有 15 位有效数字）

**为什么没有加复合索引：**

**普通的 (longitude, latitude) 复合索引有用吗：**
- 对于 `WHERE lat BETWEEN ? AND ? AND lng BETWEEN ? AND ?` 这样的查询
- 复合索引能加速，但效果有限（范围查询的第二列不太好用到索引）
- 因为经纬度是二维范围查询，普通 B-Tree 索引效率不高

**真正的地理空间查询需要什么索引：**
- **空间索引（SPATIAL INDEX）**
- MySQL 支持空间索引（MyISAM 原生支持，InnoDB 5.7+ 也支持）
- 空间索引基于 R-Tree 结构，专门优化地理空间查询
- 比普通复合索引快很多

**为什么没加：**

1. **字段类型不对**：
   - 要加空间索引，字段类型必须是 `POINT`（或其他地理类型）
   - 当前是两个 DECIMAL 字段，加不了空间索引
   - 只能加普通的复合索引

2. **数据量小**：
   - 商家数量不多（几千个），全表扫描也能接受
   - 没必要加索引
   - 但如果量大了就会慢

3. **当前只用了 ST_Distance_Sphere 计算距离，没有空间过滤**：
   - `MerchantService.nearby()` 的 SQL 是怎样的？
   - 如果是 `SELECT * FROM merchant ORDER BY ST_Distance_Sphere(...) LIMIT 10`
   - 那是全表计算距离后排序，索引没用
   - 应该先加空间范围过滤（MBRContains），再计算距离排序

**怎么优化地理查询：**

**方案一：加普通复合索引 + 范围过滤**
```sql
WHERE lat BETWEEN ? AND ?
  AND lng BETWEEN ? AND ?
ORDER BY ST_Distance_Sphere(...)
LIMIT 10
```
- 加 `(lat, lng)` 复合索引
- 先过滤出矩形范围内的商家，再计算距离排序
- 性能比全表计算好很多

**方案二：改用 POINT 类型 + 空间索引（推荐）**
```sql
location POINT NOT NULL SRID 4326,
SPATIAL INDEX idx_location(location)

SELECT * FROM merchant
WHERE ST_Distance_Sphere(location, ST_SRID(POINT(?, ?), 4326)) < 5000
ORDER BY ST_Distance_Sphere(location, ST_SRID(POINT(?, ?), 4326))
LIMIT 10
```
- 空间索引 + 空间函数，性能最好
- 但代码改动稍大（要处理 POINT 类型）

**方案三：用 Redis Geo**
- 把商家位置存在 Redis Geo 中
- 查询附近商家直接用 Redis
- 性能最好，减轻 DB 压力
- 但要维护 Redis 和 DB 的数据一致性

**当前项目的情况：**
- 用了 `ST_Distance_Sphere`（说明 MySQL 支持空间函数）
- 但字段是两个 DECIMAL，不是 POINT 类型
- 没有空间索引
- 商家数量少的话没问题，量大了需要优化

**方案二：类似 RedisTemplate 的 LockTemplate（推荐）**

```java
@Component
public class RedisLockTemplate {
    
    private final StringRedisTemplate redisTemplate;
    
    // 尝试获取锁，获取失败抛异常
    public void lock(String lockKey, Duration expire) {
        Boolean locked = redisTemplate.opsForValue()
            .setIfAbsent(lockKey, "1", expire);
        if (!Boolean.TRUE.equals(locked)) {
            throw new BusinessException("获取锁失败，请稍后重试");
        }
    }
    
    // 释放锁
    public void unlock(String lockKey) {
        redisTemplate.delete(lockKey);
    }
    
    // 模板方法：自动加锁、执行业务、自动释放
    public <T> T execute(String lockKey, Duration expire, Supplier<T> action) {
        lock(lockKey, expire);
        try {
            return action.get();
        } finally {
            unlock(lockKey);
        }
    }
    
    public void execute(String lockKey, Duration expire, Runnable action) {
        execute(lockKey, expire, () -> {
            action.run();
            return null;
        });
    }
}
```

**使用后 OrderService.cancel() 变成：**
```java
public void cancel(String orderNo) {
    String lockKey = "lock:order:" + orderNo;
    redisLockTemplate.execute(lockKey, Duration.ofSeconds(30), () -> {
        // 业务逻辑：查订单、校验状态、更新状态、回滚库存、退券
        // 不用关心锁的细节
    });
}
```

**测试时的好处：**
- 可以 mock `RedisLockTemplate` 的 `execute` 方法
- 或者写一个 `InMemoryLockTemplate` 用于测试（基于 ConcurrentHashMap）
- 业务逻辑的测试不需要关心 Redis 锁的实现细节

**更进一步：用 Redisson**
- Redisson 已经有 `RLock` 接口，实现了可重入、自动续期等高级特性
- 直接用 Redisson 的锁，不用自己写
- 测试时可以用 Redisson 的本地测试工具

---

### 101. ResultCode 只有 7 个错误码，没有针对业务错误码的测试。如果团队多人协作，A 新加了一个 ResultCode.ORDER_TIMEOUT(601)，B 新加了一个 ResultCode.COUPON_EXPIRED(601)，两个人的 code 冲突了——现有的代码组织方式能发现吗？运行时会产生什么混淆？

**回答：**

**现有的代码组织方式能发现吗：**
- **发现不了**。因为错误码是手动写的数字，编译器不检查重复
- 两个人分别在不同的分支加 code，合并代码时不会有冲突（因为改的是不同的行）
- 只有运行时才可能发现（但也不一定）

**运行时会产生什么混淆：**

1. **前端判断错误**：
   - 前端根据 code 做不同处理
   - code=601 既是"订单超时"又是"优惠券过期"
   - 前端可能在不该弹"订单超时"的地方弹了
   - 或者该弹"订单超时"的时候弹了"优惠券过期"

2. **问题排查困难**：
   - 看日志里的 code=601，不知道到底是哪个错误
   - 得看 message 才知道
   - 如果 message 也差不多，就更懵了

3. **监控告警混乱**：
   - 如果监控按错误码统计告警
   - 两个不同的错误被统计在一起，分不清谁多谁少

4. **更严重的情况**：
   - 如果前端根据 code 做跳转（如 401 跳登录）
   - 某个业务错误码不小心设成 401，会导致用户频繁跳登录

**为什么会出现这种问题：**
- 错误码没有分段规划
- 大家随便加，想到什么数字就用什么
- 没有 Code Review 检查错误码

**怎么预防：**

**方案一：错误码分段（最简单）**

```java
// 通用错误码：100-199
// 用户模块：200-299
// 商家模块：300-399
// 商品模块：400-499
// 订单模块：500-599
// 优惠券模块：600-699
// ...
```

- 每个模块分配一个号段
- 模块内自增，不会跨模块冲突
- 看到 code 就知道是哪个模块的错

**方案二：写单元测试（自动化检查）**

```java
@Test
void testResultCodeDuplicate() {
    ResultCode[] values = ResultCode.values();
    Set<Integer> codes = new HashSet<>();
    for (ResultCode value : values) {
        assertTrue(codes.add(value.getCode()), 
            "错误码重复: " + value.getCode());
    }
}
```

- 枚举的话，写个测试检查 code 是否重复
- CI/CD 跑测试，重复了构建失败

**方案三：用错误码前缀 + 枚举 name**
- 不用数字 code，用字符串 code（如 "ORDER_TIMEOUT"、"COUPON_EXPIRED"）
- 枚举 name 天然唯一，不会重复
- 但前后端联调时字符串比数字麻烦一点

**当前项目的 ResultCode 现状：**
- 只有 7 个错误码，而且很多业务异常都共用 600（BUSINESS_ERROR）
- 所以现在还不会冲突（因为大家都用 600）
- 但如果以后要细分错误码，就会遇到这个问题
- 建议：尽早规划错误码分段，越晚改越痛苦

---

## 二十二、日志与监控（Logging & Monitoring）

### 102. 项目没有 logback-spring.xml 配置，使用 Spring Boot 默认的日志配置。生产环境下默认的日志是滚动切割的吗？如果不配置 logback-spring.xml，日志文件会无限增长吗？logging.level.com.takeout: DEBUG 在线上会产生多少日志量？每笔订单从提交到完成会打出多少条 DEBUG 日志？

**回答：**

**Spring Boot 默认的日志策略：**

| 配置项 | 默认值 |
|-------|-------|
| 日志级别 | INFO（根级别）|
| 控制台输出 | 有（彩色）|
| 文件输出 | 默认不输出到文件 |
| 文件滚动 | 如果配置了文件，默认滚动 |
| 滚动策略 | 10MB 滚动一次，保留 7 天，最多 7 个文件 |
| 日志格式 | 固定格式（时间、级别、线程、logger、消息）|

**日志文件会无限增长吗：**
- **默认不会输出到文件**，只会输出到控制台
- 如果配置了 `logging.file.name` 或 `logging.file.path`，会输出到文件
- 输出到文件时，默认有滚动策略（10MB 滚动，保留 7 天），不会无限增长
- 当前 `application.yml` 里只有 `logging.level` 配置，没有 `logging.file` 配置
- 所以**当前日志只输出到控制台，不写文件**

**Docker 环境下的日志：**
- 应用输出到控制台（stdout/stderr）
- Docker 捕获控制台输出，存在 Docker 的日志驱动里
- Docker 默认日志驱动是 json-file，日志存在容器的可写层
- 如果不配置 Docker 日志滚动，**Docker 的日志文件会无限增长**
- 这是很多人忽略的地方：Spring Boot 不写文件，但 Docker 会收集控制台输出，越积越多

**Docker 日志滚动配置：**
```yaml
# docker-compose.yml 中配置
logging:
  driver: json-file
  options:
    max-size: "10m"
    max-file: "3"
```

**DEBUG 级别在线上的日志量：**

先看 DEBUG 会输出什么：
1. **MyBatis SQL 日志**：每条 SQL、参数、结果集
2. **业务 DEBUG 日志**：Service 里的 `log.debug()`
3. **Spring MVC 日志**：请求映射、参数绑定等（如果开了的话）
4. **Redis 操作日志**：每个 Redis 命令（如果开了的话）

**每笔订单从提交到完成的 DEBUG 日志估算：**

| 操作 | SQL/Redis 次数 | 日志行数 |
|------|---------------|---------|
| 提交订单 | 查询菜品、查询优惠券、查询地址、扣库存、写订单、写明细、清购物车、标记优惠券 | ~20-30 条 |
| 支付回调 | 查订单、更新状态 | ~5-10 条 |
| 商家接单 | 查订单、更新状态 | ~3-5 条 |
| 商家出餐 | 查订单、更新状态 | ~3-5 条 |
| 确认收货 | 查订单、更新状态 | ~3-5 条 |

**估算：一笔订单完整流程大约 30-50 条 DEBUG 日志**

**日订单量对应的日志量：**
- 日订单 1000 单 → 3-5 万条日志 / 天
- 日订单 1 万单 → 30-50 万条日志 / 天
- 日订单 10 万单 → 300-500 万条日志 / 天

按每条日志平均 200 字节算：
- 1 万单 / 天 → ~60-100 MB / 天
- 10 万单 / 天 → ~600MB-1GB / 天

**生产环境建议：**
- 日志级别改成 INFO 或 WARN
- 关键业务操作保留 INFO 日志（下单、支付、取消）
- SQL 日志关掉或只保留慢 SQL
- 配置日志滚动，限制总大小
- 接入 ELK 或 Loki 做日志聚合

---

### 103. GlobalExceptionHandler.handleException(Exception.class) 打印了 log.error("系统异常: {}", e.getMessage(), e)，但异常堆栈只在 ERROR 级别输出。如果某个 BusinessException 被当做 Exception 兜底捕获了（因为类型是 RuntimeException，BusinessException 继承自 RuntimeException），会错误地走到 handleException 的字符串匹配分支吗？BusinessException 的 handleBusiness 匹配在 Exception 之前还是之后？确定过 Spring 的异常匹配优先级吗？

**回答：**

**Spring MVC 的异常匹配优先级：**

**答案：handleBusiness 会优先匹配，不会走到 handleException**

**原因：**
- Spring 的 `@ExceptionHandler` 匹配遵循"**最精确匹配优先**"原则
- 有精确匹配 `BusinessException` 的方法，就走那个
- 没有精确匹配的，才会找父类匹配（如 `RuntimeException` → `Exception`）
- 所以 `BusinessException` 一定会走 `handleBusiness`，不会走到 `handleException`

**验证方法：**
1. 看代码顺序：`handleBusiness` 在 `handleException` 前面，但**顺序不重要**
2. 关键是：Spring 按"异常继承树的最近距离"选择处理器
3. `BusinessException` → `RuntimeException` → `Exception`
   - 到 `handleBusiness` 的距离是 0（直接匹配）
   - 到 `handleException` 的距离是 2（经过 RuntimeException）
   - 所以选 `handleBusiness`

**什么情况会走到 handleException：**
- 抛出的异常不是 `BusinessException`，也不是 `MethodArgumentNotValidException`、`ConstraintViolationException`
- 比如：NullPointerException、ClassCastException、DataAccessException 等
- 这些异常没有专门的 handler，就走到通用的 `handleException`

**那字符串匹配分支有什么用：**
- 处理非 BusinessException 的系统异常
- 比如数据库连不上、Redis 连不上、缓存反序列化失败
- 这些异常抛出来后，走到 `handleException`，然后根据异常消息判断是什么问题
- 给用户返回更友好的提示（而不是"系统繁忙"）

**字符串匹配的问题：**
- 靠 `e.getMessage().contains("xxx")` 来判断异常类型，很脆弱
- 异常消息变了（比如驱动升级改了消息），判断就失效了
- 更好的做法是根据**异常类型**来判断，而不是异常消息

**改进建议：**
```java
// 根据异常类型判断，而不是异常消息
if (e instanceof DataAccessException || 
    e.getCause() instanceof CommunicationsException) {
    msg = "数据库连接失败";
} else if (e instanceof RedisConnectionFailureException || 
           e.getCause() instanceof RedisConnectionFailureException) {
    msg = "Redis 连接失败";
}
```

**当前代码的逻辑是否正确：**
- 功能上没问题（BusinessException 不会走错）
- 但字符串匹配的方式不优雅，也不可靠
- 建议改成基于异常类型的判断

---

### 104. 健康检查接口 /api/health 返回自定义的 Map 结构，而不是 Spring Boot Actuator 的标准 Health 对象。如果后续要集成到 K8s 的 livenessProbe / readinessProbe 或 Spring Boot Admin，自定义返回格式需要额外适配。为什么不用 spring-boot-starter-actuator？不使用 Actuator 之后，你失去了哪些开箱即用的端点（metrics、info、env、heapdump）？

**回答：**

**为什么不用 Actuator：**
- 可能是**不知道有这个东西**，自己造了个轮子
- 也可能是觉得 Actuator 太重，自己写一个简单够用
- 或者是从微服务改造过来时，Actuator 被去掉了

**Spring Boot Actuator 提供的开箱即用端点：**

| 端点 | 作用 | 当前是否需要 |
|------|------|-------------|
| `/actuator/health` | 健康检查（有标准格式）| ✅ 需要（自己实现了一个简化版）|
| `/actuator/info` | 应用信息（版本、描述等）| ⚠️ 可以有 |
| `/actuator/metrics` | 应用指标（QPS、响应时间、内存等）| ❌ 当前没有 |
| `/actuator/prometheus` | Prometheus 格式的指标 | ❌ 当前没有 |
| `/actuator/env` | 环境变量、配置属性 | ❌ 当前没有 |
| `/actuator/beans` | Spring 容器中的所有 Bean | ❌ 调试用 |
| `/actuator/mappings` | 所有请求映射 | ❌ 调试用 |
| `/actuator/heapdump` | 堆转储（分析 OOM）| ❌ 排错用 |
| `/actuator/loggers` | 动态修改日志级别 | ❌ 运维用 |
| `/actuator/configprops` | 配置属性列表 | ❌ 调试用 |

**失去 Actuator 的影响：**

1. **监控困难**：
   - 没有 metrics 接口，没法接入 Prometheus + Grafana 做监控大盘
   - 不知道系统的 QPS、响应时间、错误率
   - 出问题了只能看日志，没有指标趋势图

2. **排错困难**：
   - 没有 heapdump 端点，OOM 了很难分析
   - 没有 env 端点，不知道当前生效的配置是什么
   - 没有 loggers 端点，不能动态改日志级别（要重启应用）

3. **K8s 集成麻烦**：
   - K8s 的 livenessProbe/readinessProbe 可以配置 HTTP 检查任意路径
   - 所以自定义的 `/api/health` 也能用
   - 但 Actuator 的 health 有标准格式（UP/DOWN + details），很多工具直接支持

4. **Spring Boot Admin 集成不了**：
   - Spring Boot Admin 需要 Actuator 端点来展示应用状态
   - 没有 Actuator，就用不了 SBA

**为什么不建议自己写健康检查：**
- Actuator 的 health 支持组合多个健康指标（DB、Redis、磁盘、MQ 等）
- 每个组件有自己的 HealthIndicator，自动集成
- 自己写的话，每加一个中间件都要手动加健康检查逻辑
- 当前代码只检查了 MySQL 和 Redis，以后加 RocketMQ、ES 之类的还要改

**什么时候用 Actuator 合适：**
- 生产环境，需要监控和运维
- 项目要上 K8s
- 需要接入监控系统（Prometheus、SkyWalking 等）

**当前项目的情况：**
- 自己写的健康检查能满足基本需求（MySQL + Redis 是否活着）
- 但功能太简单，没有指标、没有运维端点
- 建议：接入 Actuator，至少开启 health 和 metrics 端点
- 注意安全：生产环境要配置 `management.endpoints.web.exposure.include`，不要把所有端点都暴露出去

---

## 二十三、DTO/VO 设计与数据转换

### 105. 整个项目的数据转换全是手动 toVO() / toDTO() 方法，没有使用 MapStruct 或 BeanUtils。这种模式在字段少的时候可读性好，但如果有 30 个字段的 VO，手写 get/set 的缺点是什么？BeanUtils.copyProperties() 相比于 MapStruct 的性能劣势是多少（反射 vs 编译期生成）？

**回答：**

**手动 toVO() 的缺点：**

1. **代码量大、重复劳动**：
   - 30 个字段的 VO，就要写 30 行 get/set
   - 多个 VO 之间转换，代码量翻倍
   - 写起来无聊，容易写错

2. **容易漏字段**：
   - 字段多了，复制粘贴时容易漏掉一两个
   - 漏了如果没测试到，就是线上 bug

3. **维护成本高**：
   - Entity 加一个字段，所有相关的 toVO 都要改
   - 改起来繁琐，还容易漏

4. **可读性差**：
   - 几十行 get/set 堆在一起，看起来像流水账
   - 找某个字段的映射关系要翻半天

5. **字段名不一致时更麻烦**：
   - Entity 叫 `createTime`，VO 叫 `createdAt`
   - 手动写容易搞混

**BeanUtils.copyProperties() 的问题：**

**优点：**
- 代码少，一行搞定

**缺点：**
1. **性能差**：用反射，比手动写慢很多（几十倍到上百倍）
2. **字段名必须一致**：不一样的字段不会自动映射
3. **类型不自动转换**：比如 Long → String 不会自动转（报异常）
4. **编译期不检查**：字段名写错了，编译不报错，运行时才发现
5. **null 值也会覆盖**：源对象的 null 字段会覆盖目标对象的非 null 值
6. **嵌套对象映射麻烦**：需要手动处理嵌套

**MapStruct vs BeanUtils vs 手动 性能对比：**

| 方式 | 原理 | 性能 | 相对速度 |
|------|------|------|---------|
| 手动 get/set | 直接调用方法 | 最快 | 1x |
| MapStruct | 编译期生成代码（和手写一样）| 几乎一样 | ~1x |
| BeanUtils（Spring）| 反射 | 慢 | ~1/50 ~ 1/100 |
| BeanUtils（Apache）| 反射 | 更慢 | ~1/200 ~ 1/500 |

**为什么 MapStruct 性能和手动差不多：**
- MapStruct 是编译期注解处理器
- 编译时生成 `XxxMapperImpl` 类，里面就是手动的 get/set 代码
- 运行时直接调用，没有反射开销
- 所以性能和手写基本一致

**MapStruct 的额外好处：**
1. **编译期检查**：字段名写错了，编译报错
2. **类型自动转换**：Long → String、Date → String 等
3. **嵌套映射**：支持嵌套对象、集合映射
4. **自定义映射**：复杂的转换逻辑可以写表达式
5. **默认值**：null 时给默认值

**为什么当前项目用手动 toVO：**
- 可能不知道 MapStruct
- 或者字段少，觉得手动写也挺快
- 或者从微服务改造过来时，每个 Service 自己写了 toVO

**建议：**
- 字段少（<10个）：手动写没问题
- 字段多或转换频繁：用 MapStruct
- 不要用 BeanUtils（性能差 + 不安全）
- 当前项目字段都不算特别多，手动写能接受，但可以考虑引入 MapStruct 提高开发效率

---

### 106. OrderService.toVO() 中每次调用都 selectList(new LambdaQueryWrapper<OrderItem>()...) 查询 OrderItem 列表。如果一个页面列出 20 个订单（每个订单有 3-5 个商品），listMyOrders() 会执行 1 次分页查询 + 20 次 OrderItem 子查询。这是典型的 N+1 问题吗？为什么不用 LEFT JOIN 或 MyBatis Plus 的 @TableName(autoResultMap=true) + @TableField(exist=false) + 关联查询？

**回答：**

**是的，这是典型的 N+1 查询问题**

**什么是 N+1：**
- 1 次主查询（查出 N 条订单）
- N 次子查询（每条订单查一次明细）
- 总共 N+1 次查询

**20 个订单的话，就是 1 + 20 = 21 次查询**
- 如果每页 50 个订单，就是 51 次查询
- 订单多了，数据库压力很大

**为什么会出现 N+1：**
- MyBatis-Plus 的 `BaseMapper` 只提供单表 CRUD
- 一对多、多对多的关联查询需要自己写
- 开发者图方便，就在循环里一个个查

**为什么不用 LEFT JOIN：**

**方案一：LEFT JOIN + 手动映射（XML）**
```sql
SELECT o.*, oi.id as item_id, oi.dish_name, ...
FROM t_order o
LEFT JOIN t_order_item oi ON o.id = oi.order_id
WHERE o.user_id = ?
LIMIT ...
```
- 查询一次搞定，但结果是扁平化的（每条明细一行，订单信息重复）
- 需要手动把结果集组装成 OrderVO（包含 List<OrderItemVO>）
- 写起来麻烦

**方案二：MyBatis-Plus 的一对多（@TableName(autoResultMap=true) + @TableField(exist=false)）**
- MyBatis-Plus 支持一对多映射，但比较别扭
- 需要在 Entity 上加 `@TableField(exist = false)` 表示不是数据库字段
- 还要在 XML 里写 `collection` 映射
- 很多人嫌麻烦，宁愿 N+1

**方案三：两次查询 + 手动组装（推荐）**
```java
// 1. 查订单列表（1次查询）
Page<Order> orderPage = orderMapper.selectPage(page, queryWrapper);

// 2. 收集所有订单ID
List<Long> orderIds = orderPage.getRecords().stream()
    .map(Order::getId).toList();

// 3. 批量查询所有明细（1次查询）
List<OrderItem> allItems = orderItemMapper.selectList(
    new LambdaQueryWrapper<OrderItem>().in(OrderItem::getOrderId, orderIds)
);

// 4. 按 orderId 分组
Map<Long, List<OrderItem>> itemsByOrderId = allItems.stream()
    .collect(Collectors.groupingBy(OrderItem::getOrderId));

// 5. 组装 VO
List<OrderVO> voList = orderPage.getRecords().stream().map(o -> {
    OrderVO vo = toVO(o);
    vo.setItems(toItemVOList(itemsByOrderId.get(o.getId())));
    return vo;
}).toList();
```

- 总共 2 次查询，不管 N 是多少
- 性能比 N+1 好很多（特别是 N 大时）
- 代码也不算复杂

**为什么当前项目没优化：**
1. **订单量小**：用户订单不多，N+1 问题不明显
2. **性能没到瓶颈**：反正数据库能扛住
3. **开发图快**：先写出来再说，优化以后再说
4. **不知道更好的写法**：有些开发者只会在循环里查

**N+1 问题的危害（量级上去了之后）：**
- 数据库连接被占满（每条查询占一个连接）
- 响应时间变长（20 次查询的往返时间累积）
- 数据库 CPU 高（大量简单查询）
- 连接池耗尽后，其他请求也拿不到连接

**当前项目中还有哪些地方可能有 N+1：**
- 订单列表 → 查商家名称（每个订单查一次商家）
- 评价列表 → 查用户信息（每个评价查一次用户）
- 菜单列表 → 查菜品（每个分类查一次菜品，这个其实是查了一次全量）

**建议：**
- 列表接口都检查一下是不是 N+1
- 用"两次查询 + 分组组装"的模式优化
- 数据量大的接口优先优化

---

### 107. DishSnapshotVO 使用了 record 类型，而 MerchantVO 用的是 class。这种"同一模块内 record 和 class 混用"是渐进式重构的结果还是有意设计？record 的不可变性在某些场景下（如 Jackson 反序列化、AOP 代理）是否有限制？有没有遇到过 record 不能被动态代理的情况？

**回答：**

**是渐进式重构还是有意设计：**
- 大概率是**不同的开发者写的**，或者不同时期写的
- 看到哪个写的时候顺手用哪个
- 不太可能是"有意设计"——因为没有明显的区分规则

**怎么区分该用 record 还是 class：**

| 场景 | 推荐用 record | 推荐用 class |
|------|-------------|-------------|
| DTO/VO（纯数据载体）| ✅ 适合 | 也可以 |
| 需要 setters（修改字段）| ❌ 不行 | ✅ 适合 |
| 需要继承 | ❌ final 类，不能继承 | ✅ 可以 |
| 需要 AOP 代理 | ❌ 有限制 | ✅ 可以 |
| 字段很少（2-3个）| ✅ 很适合 | 有点啰嗦 |
| 字段很多（>10个）| ⚠️ 构造函数太长 | ✅ 可读性好 |
| 有业务方法 | ❌ 不适合 | ✅ 适合 |

**record 的不可变性在 Jackson 反序列化时有限制吗：**
- **基本没有限制**，Jackson 支持 record 的反序列化
- Jackson 2.12+ 正式支持 Java record
- 反序列化时，Jackson 会调用 record 的 canonical constructor（全参数构造函数）
- 所以 `@Schema`、`@JsonProperty` 等注解可以加在 record 组件上

**record 和 AOP 代理的关系：**
- **record 类是 final 的**，不能被 CGLIB 代理（CGLIB 通过生成子类实现代理）
- JDK 动态代理是基于接口的，如果 record 实现了接口，可以用 JDK 代理
- 但 record 通常用作 DTO，不会被 AOP 代理（Service 才会被代理）
- 所以**实际项目中基本不会遇到"record 不能被代理"的问题**

**record 的其他限制：**

1. **不能继承其他类**：
   - record 隐式继承自 `java.lang.Record`
   - Java 不支持多继承，所以不能再继承别的类

2. **字段都是 final 的**：
   - 创建后不能修改
   - 如果需要修改，得 new 一个新的 record 对象

3. **不能有非组件字段**：
   - 所有字段都必须在构造器参数中声明
   - 不能有额外的实例字段（可以有静态字段）

4. **构造器写法特殊**：
   - 紧凑构造器（compact constructor）：`public DishSnapshotVO { ... }`
   - 用来做参数校验

**什么时候用 record 会踩坑：**

1. **MyBatis-Plus 的 Entity 不能用 record**：
   - Entity 需要 setter，record 没有
   - MyBatis-Plus 反射设置字段值会失败

2. **Spring MVC 的表单参数绑定可能有问题**：
   - GET 请求的参数绑定（@ModelAttribute）需要无参构造函数 + setter
   - record 没有无参构造函数（只有全参的）
   - 所以查询参数对象（如 MerchantPageQuery）通常用 class

3. **需要构建器（Builder）时**：
   - 字段多了，全参数构造函数太长
   - class 可以加 `@Builder`，record 也可以但稍麻烦

**当前项目的混用情况：**
- 用 record 的：`DishSnapshotVO`、`StockDeductItem`、各种 Request（`SubmitOrderRequest` 等）
- 用 class 的：`MerchantVO`、`OrderVO`、`UserVO`、`CouponVO` 等
- 看起来像是：简单的、字段少的用 record，复杂的用 class
- 但也不完全是，比如 `DishRequest` 是 record 但字段也不少

**建议：**
- DTO/VO 优先用 record（不可变、简洁）
- 需要修改的对象用 class
- 保持一致，不要混用得太随意

---

## 二十四、枚举与常量管理

### 108. 项目中唯一存在的枚举是 UserRole.java（CUSTOMER、ADMIN、MERCHANT），但订单状态（Order.status：1~7）、商家状态（Merchant.status：0~4）、用户状态（User.status：0/1）全部用魔法数字硬编码在 Service 和 Controller 中。如果看到一个「把数字改成常量」的提交要求——你会建议新增常量类、枚举，还是用什么方式？团队怎么保证后续开发不再引入新的魔法数字？

**回答：**

**建议用枚举，而不是常量类**

**为什么枚举比常量类好：**

| 特性 | 常量类（public static final int）| 枚举（enum）|
|------|------------------------------|------------|
| 类型安全 | ❌ 任何 int 都能传，编译器不检查 | ✅ 只能传枚举值，编译期检查 |
| 可读性 | 看到 2 不知道什么意思（得查常量名）| 看到 OrderStatus.WAIT_RECEIVE 就懂 |
| 可扩展 | 只有数字，不能附加信息 | 可以加方法、字段、行为 |
| 遍历 | 不能遍历所有常量 | 可以用 values() 遍历所有枚举值 |
| Switch | 可以用 int switch | 可以用枚举 switch（更优雅）|

**具体怎么改：**

**1. 订单状态枚举：**
```java
public enum OrderStatus {
    WAIT_PAY(1, "待支付"),
    WAIT_RECEIVE(2, "待接单"),
    COOKING(3, "备餐中"),
    DELIVERING(5, "配送中"),
    COMPLETED(6, "已完成"),
    CANCELED(7, "已取消");
    
    private final int code;
    private final String desc;
    
    OrderStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
    
    public int getCode() { return code; }
    public String getDesc() { return desc; }
    
    public static OrderStatus of(int code) {
        for (OrderStatus s : values()) {
            if (s.code == code) return s;
        }
        throw new IllegalArgumentException("未知订单状态: " + code);
    }
}
```

**2. 商家状态枚举：**
```java
public enum MerchantStatus {
    PENDING_AUDIT(0, "审核中"),
    OPEN(1, "营业中"),
    CLOSED(2, "打烊"),
    BANNED(3, "封禁"),
    AUDIT_REJECTED(4, "审核拒绝");
    // ...
}
```

**3. Entity 中怎么存：**
- 数据库字段还是 int（存 code）
- Entity 字段可以直接用枚举类型
- MyBatis-Plus 支持枚举映射（`@EnumValue` 注解指定存哪个字段）

```java
// Order.java
@EnumValue
private OrderStatus status;  // 数据库存 1/2/3...，Java 里是枚举
```

**怎么保证不再引入新的魔法数字：**

1. **代码审查（Code Review）**：
   - Review 时看到魔法数字（如 status == 1），打回要求改枚举
   - 形成规范后，大家就习惯了

2. **静态代码检查**：
   - 用 SonarQube、Alibaba Java Coding Guidelines 等工具
   - 检测魔法数字，告警

3. **团队规范文档**：
   - 明确规定：状态类、类型类字段必须用枚举
   - 常量只能用在真正的"常数"（如圆周率、默认分页大小）

4. **老代码逐步替换**：
   - 不要一上来就全量改，容易出 bug
   - 改到哪里改哪里，慢慢替换
   - 新代码必须用枚举

**当前项目为什么只有 UserRole 用了枚举：**
- 可能 UserRole 是最早写的，写的人习惯用枚举
- 后来写订单、商家的人图省事，直接用 int 了
- 或者是从微服务改造过来时，不同模块的风格不一样

**魔法数字的危害：**
1. 可读性差：`if (status == 2)` 谁知道 2 是什么意思
2. 容易写错：`== 3` 写成 `== 2`，编译器不报错，业务逻辑错了
3. 修改麻烦：要改状态值，得全局搜 1/2/3，容易漏

---

### 109. OrderService 中所有状态流转都是 updateStatusWithLock(order, expected, newStatus, reason) 调用。如果未来新加一个"配送异常"状态（status=8），需要在多少处地方同步修改：OrderService 流转方法 × N、submit() 中的状态校验 × 1、cancel() 中的状态判断 × 1、前端状态显示组件 × 3（H5 + 商家 + 管理台）？最容易被遗漏的是哪几处？

**回答：**

**新增一个状态需要改的地方：**

**后端（Java）：**

1. **OrderService 中的状态流转：**
   - `submit()`：初始状态设置（status=1）
   - `payOrder()`：校验待支付，改成待接单（1→2）
   - `accept()`：校验待接单，改成备餐中（2→3）
   - `reject()`：校验待接单，改成已取消
   - `ready()`：校验备餐中，改成配送中（3→5）
   - `complete()`：校验配送中，改成已完成（5→6）
   - `cancel()`：判断哪些状态可以取消
   - `updateStatusWithLock()`：通用更新方法（这个不用改）
   - 新增"配送异常"相关的流转方法

2. **状态校验逻辑：**
   - `submit()` 中的各种前置校验
   - `cancel()` 中的状态判断
   - 评价、退款等操作的状态前置校验

3. **其他模块：**
   - 购物车、优惠券、评价等模块中涉及订单状态判断的地方
   - 比如：只有已完成的订单才能评价

**前端（3个项目）：**

4. **H5 用户端：**
   - 订单列表的状态显示
   - 订单详情的状态显示
   - 操作按钮的显示/隐藏（什么状态能取消、能确认收货等）

5. **商家端：**
   - 订单列表的状态显示
   - 订单状态流转的按钮（接单、出餐等）
   - 订单筛选的状态选项

6. **管理台：**
   - 订单列表的状态显示
   - 订单筛选的状态选项
   - 订单详情的状态显示

**数据库：**
7. **SQL 脚本注释**：`init.sql` 中 status 字段的 comment

**文档：**
8. **API 文档**：状态枚举的说明
9. **业务文档**：状态流转图更新

**估算：至少 10-15 处地方需要改**

**最容易被遗漏的是哪些：**

1. **边缘场景的状态判断**：
   - 比如"已完成的订单才能评价"——ReviewService 里有个 `order.getStatus() == 6` 的判断
   - 新增状态后，可能"配送异常"的订单也能评价？也不能？
   - 这种分散在其他模块的状态判断最容易漏

2. **前端操作按钮的显隐逻辑**：
   - 比如什么状态下显示"取消订单"按钮
   - 什么状态下显示"联系商家"按钮
   - 前端状态多了，很容易漏改几个页面

3. **订单筛选/搜索**：
   - 管理台的订单筛选下拉框
   - 商家端的订单状态 Tab
   - 新增了状态，这些地方要加选项

4. **统计/报表**：
   - 如果有"各状态订单数量"的统计
   - 新增状态后，统计维度要加

5. **测试用例**：
   - 新增状态后，要补对应的测试
   - 这个经常忘

**怎么减少遗漏：**

1. **用状态机模式**：
   - 所有状态流转集中在一个状态机里
   - 新增状态时，只要改状态机的配置
   - 但这只能解决后端的流转，前端还是要改

2. **状态列表接口**：
   - 后端提供一个"订单状态列表"接口
   - 前端动态渲染状态选项，不用硬编码
   - 但按钮显隐逻辑还是要前端写

3. **全局搜索**：
   - 改完后，全局搜 `getStatus()`、`status == `、`status:`
   - 检查每一处是否需要同步修改

4. **Code Review 检查清单**：
   - Review 时对照检查清单，确保都改到了

**为什么会有这么多地方要改：**
- 因为状态是业务的核心，很多逻辑都依赖状态判断
- 这是正常的，新增业务状态必然要改很多地方
- 关键是要改全，不要漏

---

### 110. UserRole 枚举使用了 name() 作为序列化方式，Jackson 序列化后是小写还是大写？前端传递的 role 值是 ADMIN、admin 还是 "admin"？如果是前端传小写，valueOf() 会直接抛错（IllegalArgumentException），现有代码用 try-catch 吞掉了，但 CUSTOMER 是不安全的默认值——如果传了 ADMINI（多了一个 I），用户会被降级为 CUSTOMER 吗？权限会受损吗？

**回答：**

**Jackson 序列化后是大写还是小写：**
- `UserRole.ADMIN.name()` 返回 `"ADMIN"`（大写）
- Jackson 默认序列化枚举时，用的是 `name()` 方法
- 所以序列化后是 **大写**：`{"role": "ADMIN"}`

**前端传什么值：**
- 取决于前端怎么写
- 如果后端返回的是大写，前端传回来也是大写（直接用返回值），那就没问题
- 但如果前端自己拼了小写的 role，就会出问题

**valueOf() 传小写会怎样：**
- `UserRole.valueOf("admin")` 会抛 `IllegalArgumentException`
- 因为枚举的 valueOf() 是**大小写敏感**的
- 必须完全匹配 name() 的值

**现有代码的 try-catch + CUSTOMER 默认值：**

如果代码是这样的：
```java
try {
    UserRole role = UserRole.valueOf(roleStr);
    return role;
} catch (Exception e) {
    return UserRole.CUSTOMER;  // 默认降级为普通用户
}
```

**问题分析：**

**场景 1：前端传 "ADMINI"（多打了一个 I）**
- valueOf 抛异常 → catch 返回 CUSTOMER
- 管理员被降级为普通用户
- **权限受损**：管理员看不到管理台页面，做不了管理操作

**场景 2：前端传 "admin"（小写）**
- valueOf 抛异常 → catch 返回 CUSTOMER
- 同样被降级

**场景 3：攻击者传一个不存在的 role**
- 也会被降级为 CUSTOMER
- 这个相对安全（权限变小了）

**但更大的问题是：登录时的 role 是哪来的？**
- 如果是登录时从数据库查的（`user.getRole()`），那是后端控制的，不会有问题
- 如果是前端传什么 role，后端就用什么，那就是**安全漏洞**
- 正常设计：登录后的角色是从数据库/Token 里取的，前端不能改

**当前项目的 UserRole 是怎么用的：**
- `UserContext` 里的 role 是从 JWT Token 里解析的
- JWT 是后端签发的，所以 role 值是后端控制的
- 前端不能随便改 role（改了 Token 签名就无效了）
- 所以这个"降级为 CUSTOMER"的问题在正常流程下不会触发

**但 try-catch 吞异常 + 默认值仍然是不好的设计：**
1. **静默失败**：参数传错了，调用者不知道，以为传对了
2. **调试困难**：为什么我传的 ADMIN 变成了 CUSTOMER？找半天
3. **安全隐患**：如果某个场景下 role 是前端可控的，可能导致权限绕过

**改进建议：**

1. **不要吞异常**：
   ```java
   // 传错了直接抛异常，让前端知道传错了
   try {
       return UserRole.valueOf(roleStr.toUpperCase());
   } catch (IllegalArgumentException e) {
       throw new BusinessException(ResultCode.PARAM_ERROR, "无效的角色: " + roleStr);
   }
   ```

2. **忽略大小写**：
   - 先转大写再 valueOf
   - 前端传大写小写都能识别

3. **用 @JsonCreator 自定义反序列化**：
   ```java
   @JsonCreator
   public static UserRole fromString(String value) {
       return UserRole.valueOf(value.toUpperCase());
   }
   ```
   - Jackson 反序列化时自动调用这个方法
   - 可以处理大小写问题

4. **用数字存枚举（更稳定）**：
   - 数据库存 int（如 0=CUSTOMER, 1=MERCHANT, 2=ADMIN）
   - 前后端传数字，不受大小写影响
   - 但可读性差一点

---

## 二十五、ID 生成策略

### 111. SnowflakeIdUtil 使用固定参数 new SnowflakeIdUtil(1, 1) 创建单例，workerId 和 dataCenterId 都是 1。单体部署时固定值没问题，但如果未来拆回微服务（5 个节点），每个服务实例的 workerId 怎么分配？手动配置还是自动发现？如果两个服务实例的 workerId 相同，生成的 ID 会重复吗？

**回答：**

**两个实例 workerId 相同会怎样：**

**会重复！**
- 雪花算法的唯一性依赖：时间戳 + 数据中心ID + 工作节点ID + 序列号
- 如果两个实例在**同一毫秒**内生成 ID，且：
  - 时间戳相同
  - workerId + dataCenterId 相同
  - 序列号都从 0 开始自增
- 那么生成的 ID 就**完全一样**

**重复的概率：**
- 如果两个实例 workerId 相同，但启动时间不同，低并发下可能不会撞（因为时间戳错开了）
- 但高并发下，同一毫秒都生成 ID，就一定会撞
- 特别是分布式部署，流量大的时候，重复概率很高

**workerId 怎么分配（5个节点）：**

**方案一：手动配置（最简单）**
- 每个实例的 `application.yml` 配置不同的 workerId
- 或通过环境变量 `WORKER_ID=1`、`WORKER_ID=2` ... 传入
- 优点：简单，不需要额外组件
- 缺点：容易配错，扩缩容麻烦

**方案二：基于 IP 计算**
- 取 IP 地址的最后一段 mod 32 作为 workerId
- 只要 IP 不冲突，workerId 就不冲突
- 优点：自动分配，不用手动配
- 缺点：IP 最后一位可能重复（不同网段）

**方案三：Redis / Zookeeper 分配**
- 应用启动时去 Redis 拿一个 workerId（INCR 自增）
- 用完释放（或定期续租）
- 优点：自动分配，不重复
- 缺点：依赖 Redis/ZK，增加复杂度

**方案四：用 MyBatis-Plus 的默认策略**
- MyBatis-Plus 的 `IdentifierGenerator` 默认实现会自动读取网卡 MAC 地址生成 workerId
- 单机多实例可能冲突，但不同机器基本不会
- 优点：开箱即用
- 缺点：容器环境下可能 MAC 地址相同（Docker 默认）

**Docker 环境下的特殊问题：**
- Docker 容器的 MAC 地址是自动生成的，但可能重复吗？
- 默认情况下，同一宿主机上的容器 MAC 地址不重复
- 但不同宿主机可能重复（概率低）
- 而且容器重建后 MAC 地址可能变

**当前项目的 SnowflakeIdUtil 是自定义的还是用的 MP 的：**
- 是自定义的 `SnowflakeIdUtil`，有单例
- 不是 MyBatis-Plus 自带的
- 所以 workerId 固定为 1

**拆微服务后的建议：**
1. **短期（2-3个节点）**：手动配置 workerId，每个实例不一样
2. **中期（5-10个节点）**：基于 Redis 自动分配
3. **长期**：用专门的 ID 生成服务（如百度 UidGenerator、美团 Leaf）

**还有一种思路：用 UUID**
- 简单，完全不用考虑 workerId
- 但 UUID 是字符串，占空间大，无序，索引性能差
- 不适合作为数据库主键

---

### 112. 雪花算法代码中，System.currentTimeMillis() 获取的服务器时间一旦发生时钟回拨（NTP 同步、运维误操作），会抛出 RuntimeException("时钟回拨，拒绝生成 ID")。但这个异常没有被 GlobalExceptionHandler 捕获（不是 BusinessException），会直接返回 500 给前端。如果时钟回拨了 1 秒，系统在回拨期间不能生成任何订单号——这个设计在真实线上环境可以接受吗？有没有更好的处理方式？

**回答：**

**当前设计的问题：**

1. **直接抛 RuntimeException，返回 500**：
   - 用户看到"系统繁忙"，不知道怎么回事
   - 如果是下单操作，用户可能重复提交，导致其他问题

2. **回拨期间完全不可用**：
   - 时钟回拨了 1 秒，这 1 秒内所有生成 ID 的操作都失败
   - 下单、支付等核心功能不可用
   - 1 秒虽然不长，但高并发下可能影响很多请求

3. **异常类型不对**：
   - 抛 RuntimeException 太笼统
   - 应该抛自定义的业务异常或系统异常

**这个设计可以接受吗：**
- **小公司、低并发**：可以接受。时钟回拨概率很低，就算发生了，1 秒后恢复，影响不大
- **大公司、高并发**：不能接受。每秒几千单，1 秒就是几千单失败，用户体验差，还可能引发其他问题

**更好的处理方式：**

**方案一：等待直到时间追上（简单，推荐）**
```java
// 如果时钟回拨了，就等一会儿，等时间追上 lastTimestamp
while (timestamp < lastTimestamp) {
    try {
        Thread.sleep(lastTimestamp - timestamp);
    } catch (InterruptedException e) {
        // ...
    }
    timestamp = System.currentTimeMillis();
}
```
- 优点：简单，最终还是能生成唯一 ID
- 缺点：回拨时间长的话，线程会阻塞很久

**方案二：回拨时间短就等待，长就抛异常**
```java
long offset = lastTimestamp - timestamp;
if (offset <= 5) {  // 回拨 5ms 以内，等等
    Thread.sleep(offset);
    timestamp = System.currentTimeMillis();
} else {  // 回拨太多，抛异常
    throw new RuntimeException("时钟回拨过大，拒绝生成 ID");
}
```
- 兼顾了可用性和安全性
- 小回拨自动恢复，大回拨报警

**方案三：用备用 workerId（美团 Leaf 方案）**
- 维护多个 workerId
- 时钟回拨时，切换到另一个 workerId 继续生成
- 复杂但可用性高

**方案四：百度 UidGenerator 的方案**
- 用"时间差"而不是绝对时间戳
- 启动时记录启动时间，之后用 `当前时间 - 启动时间` 作为时间戳部分
- 只要启动时间不回拨就行（运行时回拨不影响）
- 但要求每次重启的 workerId 不一样

**方案五：完全不用雪花算法**
- 用数据库自增 ID：简单，但分布式困难
- 用 UUID：简单，但无序、占空间
- 用号段模式（Leaf-segment）：性能好，不依赖时钟

**时钟回拨的常见原因：**
1. NTP 时间同步（最常见，一般回拨几毫秒到几秒）
2. 运维手动改时间（人为操作）
3. 虚拟机迁移（宿主机时间不同步）
4. 闰秒（极少见）

**降低时钟回拨概率的措施：**
- 配置合理的 NTP 同步策略（不要太频繁，用平滑同步）
- 关闭虚拟机的时间同步（如果宿主机时间不准）
- 应用启动时检查时间是否合理

**当前项目的实际情况：**
- 外卖系统，订单生成是核心功能
- 时钟回拨 1 秒，可能导致几十个用户下单失败
- 建议：改成"短时间回拨就等待"的策略，提升可用性
- 同时把异常改成 BusinessException，前端可以友好提示

---

### 113. Order 实体使用 IdType.ASSIGN_ID（雪花 ID），而订单号 orderNo 又单独使用 SnowflakeIdUtil.generate() 生成——也就是说一条订单记录有 id 和 orderNo 两个雪花 ID。为什么需要两个雪花 ID？直接使用 id 作为订单号会有什么问题？

**回答：**

**为什么需要两个雪花 ID：**

**原因 1：业务含义不同**
- `id`：数据库主键，内部使用，不对外暴露
- `orderNo`：订单号，给用户看的，对外暴露
- 两者虽然都是唯一的，但用途不同

**原因 2：订单号可能需要有业务含义**
- 很多系统的订单号不是纯数字，可能有前缀（如 `ORD2024010100001`）
- 或者包含时间、商家编码等信息
- 而数据库主键就是纯 ID，没有业务含义

**原因 3：安全性考虑**
- 如果订单号就是主键 ID，用户可以通过订单号推测订单总量
- 比如用户看到自己的订单号是 1000，就知道系统总共只有约 1000 个订单
- 用不同的 orderNo，可以用不同的生成规则，不暴露内部 ID

**原因 4：历史原因（微服务改造遗留）**
- 微服务架构下，订单号可能是由专门的"ID生成服务"生成的
- 数据库主键是各服务自己的
- 改造为单体后，两套 ID 生成逻辑都保留了

**直接用 id 作为订单号会有什么问题：**

1. **暴露订单量**：
   - 雪花 ID 虽然不是严格自增，但也是趋势递增的
   - 攻击者可以根据订单号大致估算订单量

2. **订单号格式不友好**：
   - 雪花 ID 是 19 位长数字（如 1750123456789012345）
   - 用户记不住，客服沟通也麻烦
   - 真实订单号通常更短或有格式

3. **扩展性差**：
   - 以后如果订单号需要加前缀、加日期、加商家编码
   - 主键 ID 不能随便改格式
   - 但 orderNo 可以随便改，只要保证唯一就行

4. **分库分表问题**：
   - 以后分库分表，主键 ID 可能要加分片标识
   - 订单号对外暴露，不想暴露分片信息

**真实电商的订单号通常长什么样：**
- `20240101 + 商家ID + 序列号`
- `时间戳 + 随机数`
- `前缀 + 日期 + 流水号`
- 16-20 位数字，不一定纯雪花

**当前项目的 orderNo：**
- `String.valueOf(SnowflakeIdUtil.generate())`
- 就是把雪花 ID 转成字符串，没有加任何业务前缀
- 和 `id` 都是雪花 ID，只是两个不同的雪花 ID（同一个雪花生成器生成的，所以不会重复）

**那两个雪花 ID 浪费吗：**
- 有点浪费。既然格式一样，为什么不直接用 id？
- 但"分开"这个设计是对的——**内部ID和对外业务单号应该分离**
- 只是当前没有体现出差异（都是纯数字雪花ID）
- 以后要改订单号格式（加前缀、变短）就很方便，不用改主键

**建议：**
- 当前可以先用着，两个 ID 分开是最佳实践
- 以后可以给 orderNo 加业务前缀（如 `T20240101xxxxx`）
- 或者用更短的订单号（更友好）

---

## 二十六、配置管理（Configuration）

### 114. application.yml 中 JWT secret 硬编码为 takeout-system-jwt-secret-key-2024-very-long（看起来是测试密钥），但密码要求至少 32 字节，这个字符串正好 44 个字符。如果代码通过 Git 公开（比如开源或外泄），攻击者可以用这个 secret 签发任意角色的 Token。有没有考虑过使用环境变量 ${JWT_SECRET} 或外部配置中心？为什么没有？

**回答：**

**为什么没有用环境变量或配置中心：**

1. **开发图方便**：
   - 本地开发，密钥硬编码在 yml 里，拿起来就用
   - 不用配环境变量，不用装配置中心
   - 很多项目初期都是这样

2. **安全意识不足**：
   - 觉得"只是个测试项目，没人会攻击"
   - 不知道硬编码密钥的风险
   - 或者知道但觉得"反正不上生产"

3. **从模板项目复制来的**：
   - 可能是从某个教程或模板项目抄的配置
   - 模板里就是硬编码的

4. **还没到生产阶段**：
   - 项目可能还在开发/测试阶段
   - 想着"上生产前再改"
   - 但往往上生产时忘了改

**硬编码密钥的风险：**

1. **代码泄露 = 完全失守**：
   - 代码上传到公开 GitHub，所有人都能看到密钥
   - 攻击者可以伪造任意用户的 Token，包括管理员
   - 想干什么就干什么

2. **内部人员风险**：
   - 所有开发人员都知道密钥
   - 离职人员也知道
   - 人多了就不安全

3. **轮换困难**：
   - 密钥硬编码在代码里，要换就得改代码、重新部署
   - 不能动态轮换

**怎么改：**

**方案一：环境变量（最简单，推荐）**
```yaml
jwt:
  secret: ${JWT_SECRET:takeout-system-jwt-secret-key-2024-very-long}
```
- 有环境变量就读环境变量，没有就用默认值（开发用）
- 生产环境通过环境变量注入真实密钥
- 零成本，立竿见影

**方案二：配置中心（推荐中大型项目）**
- Nacos / Apollo / Spring Cloud Config
- 配置集中管理，动态刷新
- 密钥加密存储
- 但需要额外部署配置中心

**方案三：加密配置（Jasypt）**
- 用 `jasypt-spring-boot-starter`
- yml 中的密钥是加密后的密文
- 启动时用密钥解密
- 但解密的密钥（盐）还是要通过环境变量传（终极问题）

**方案四：K8s Secret**
- 如果部署在 K8s，用 Secret 管理敏感配置
- 通过环境变量或文件挂载注入

**当前项目的建议：**
- 立刻改成环境变量方式（5 分钟搞定）
- 默认值保留开发用的密钥（方便本地启动）
- 生产环境必须通过环境变量传不同的密钥
- 代码仓库里的密钥就当是"开发环境密钥"，生产用别的

**除了 JWT secret，还有哪些硬编码的敏感信息：**
- 数据库密码（root/root）
- Redis 密码（无密码）
- 这些也应该通过环境变量注入
- 特别是数据库密码，泄露了很危险

---

### 115. 项目有两个 profile：默认（application.yml）和 docker（application-docker.yml）。spring.profiles.active 通过环境变量 SPRING_PROFILES_ACTIVE=docker 传入，但 application.yml 中配置了 spring.datasource.password: root。如果生产环境改用不同密码，是通过覆盖 profile 中的 password、环境变量、还是 jasypt 加密？当前方案支持以上哪种？

**回答：**

**当前方案支持的配置覆盖方式（优先级从高到低）：**

1. **命令行参数**（最高）
2. **环境变量**（OS environment variables）
3. **application-{profile}.yml**（profile 配置）
4. **application.yml**（默认配置）（最低）

这是 Spring Boot 的外部化配置优先级规则。

**具体来说：**

| 方式 | 当前支持吗 | 怎么用 |
|------|----------|-------|
| 环境变量覆盖 | ✅ 支持 | `SPRING_DATASOURCE_PASSWORD=xxx` |
| application-prod.yml 覆盖 | ✅ 支持 | 新建 `application-prod.yml`，配密码 |
| 命令行参数覆盖 | ✅ 支持 | `--spring.datasource.password=xxx` |
| Jasypt 加密 | ❌ 不支持 | 需要引入依赖 |

**环境变量怎么映射到配置项：**
- `spring.datasource.password` → 环境变量 `SPRING_DATASOURCE_PASSWORD`
- 规则：点换成下划线，全大写，横杠去掉
- Spring Boot 自动识别

**生产环境推荐用哪种：**

**如果用 Docker / K8s 部署：**
- 推荐**环境变量**方式
- Docker Compose：
  ```yaml
  environment:
    - SPRING_DATASOURCE_PASSWORD=真实密码
    - JWT_SECRET=真实密钥
  ```
- K8s：用 Secret + 环境变量

**如果是传统部署（物理机/虚拟机）：**
- 推荐**独立的 application-prod.yml**
- 放在服务器上，不打进 jar 包
- 启动时指定 `spring.profiles.active=prod`
- 或者用 `spring.config.additional-location` 指定外部配置文件

**为什么不推荐 Jasypt：**
- Jasypt 可以加密配置，但解密密钥本身也是个秘密
- 解密密钥通过环境变量传，那和直接传密码有什么区别？
- 唯一好处：配置文件泄露了不会直接暴露密码
- 但增加了复杂度，中小项目没必要

**当前项目的最佳实践：**

1. **application.yml**：默认配置，开发用，密码是 root/root（默认值）
2. **application-docker.yml**：Docker 环境配置（开发/测试用），改一下 host 就行
3. **生产环境**：通过环境变量覆盖密码和密钥
   - `SPRING_DATASOURCE_PASSWORD=生产数据库密码`
   - `JWT_SECRET=生产JWT密钥`
   - `SPRING_PROFILES_ACTIVE=prod`（如果有 prod profile）

**注意事项：**
- 环境变量方式虽然方便，但要注意环境变量的安全性
- Docker Compose 文件里明文写密码，其他人能看到
- 生产环境的 docker-compose.yml 不要提交到代码仓库
- 或者用 `.env` 文件，`.env` 不提交到 Git

---

### 116. application.yml 中 spring.data.redis.lettuce.pool.max-active: 8，但 orderService.submit() 方法中同时使用 Redis 做分布式锁和库存扣减，高峰期 100 并发下单请求同时到来，8 个连接池会耗尽吗？Lettuce 连接池耗尽时会阻塞还是抛出异常？超时时间在哪里配置的？

**回答：**

**先澄清：Lettuce 的"连接池"和传统理解不太一样**

**Lettuce 的特点：**
- Lettuce 是基于 Netty 的异步非阻塞 Redis 客户端
- 它的"连接池"不是传统意义上的"每个请求一个连接"
- Lettuce 可以用一个连接处理多个并发请求（因为是异步的、多路复用的）
- 所以 8 个连接可以支持很高的并发

**但如果你用的是同步 API（`redisTemplate.opsForValue().get()`）：**
- Spring Data Redis 默认用的是 Lettuce 的同步 API
- 同步调用会占用连接直到返回
- 所以并发高了还是需要连接池

**100 并发下单，8 个连接够吗：**

我们来估算一下：
- 一次 `submit()` 操作，Redis 操作有几次？
  - 库存扣减 Lua：1 次（最耗时）
  - 可能还有分布式锁的 setIfAbsent：1 次
  - 优惠券分布式锁：1 次
  - 总共约 2-3 次 Redis 操作

- 每次 Redis 操作耗时多少？
  - 本地 Redis：~1ms
  - 远程 Redis：~5-10ms

- 假设每次 Redis 操作 5ms，一次下单 3 次操作
- 8 个连接，每个连接每秒能处理 `1000ms / 5ms = 200` 次操作
- 8 个连接总共：`8 × 200 = 1600` 次操作/秒
- 每秒能支撑的下单数：`1600 / 3 ≈ 500+` 单/秒

**结论：100 并发（大约 100 QPS）下，8 个连接完全够用**
- 真要打满 8 个连接，可能需要 500+ QPS
- 当前项目规模远远到不了

**连接池耗尽时会怎样：**

**Lettuce 的行为：**
- 连接池耗尽时，新的请求会**阻塞等待**，直到有连接归还
- 阻塞超时时间由 `max-wait` 配置决定
- 超时后抛出 `RedisConnectionFailureException`（或类似异常）

**默认配置：**
- `max-wait`：默认 -1（无限等待）
- 这很危险！如果 Redis 慢了，所有请求都卡在那里，线程池也会耗尽

**怎么配置超时：**
```yaml
spring:
  data:
    redis:
      lettuce:
        pool:
          max-active: 8
          max-idle: 8
          min-idle: 0
          max-wait: 1000ms  # 最多等 1 秒，超时抛异常
```

**当前项目的配置：**
- 只有 `max-active: 8`、`max-idle: 8`、`min-idle: 0`
- 没有配置 `max-wait`
- 所以是**无限等待**（默认值 -1）

**无限等待的风险：**
- 如果 Redis 响应慢或挂了
- 应用的所有请求都阻塞在等 Redis 连接
- Tomcat 线程池被占满，整个应用不响应
- 这是"级联故障"的典型场景

**建议：**
- 加上 `max-wait: 1000ms` 配置
- 加上合理的 `timeout`（当前有 5000ms，还行）
- 加上熔断降级（Redis 挂了的话，有些功能可以降级，比如缓存直接查 DB）

**补充：连接数多少合适：**
- 不是越多越好。连接太多，Redis 服务端压力也大
- 一般应用：8-16 个连接足够
- 高并发应用：32-64 个
- Redis 单线程模型，太多连接也没用，瓶颈在 Redis 服务端

---

## 二十七、前端架构与后端关系

### 117. 项目有三个独立前端工程（H5、merchant-web、admin-web），但后端是一个单体。三个前端工程共享同一个认证接口 /api/auth/login，但各自有不同的前端路由和权限页面。如果某个接口（如 GET /api/merchant/nearby）需要新增一个查询参数，修改链路是后端改 1 处 + 前端改 1 处还是 3 处？如果三个前端对同一个 API 的使用方式不一致，这是谁的责任？

**回答：**

**新增一个查询参数要改几处：**

**取决于谁在用这个接口：**

- 如果只有 H5 用户端用附近商家 → **后端 1 处 + 前端 1 处**
- 如果三个前端都用这个接口 → **后端 1 处 + 前端 3 处**
- 比如 `/api/merchant/nearby` 是 H5 首页用的，商家端和管理台不用 → 只改 H5

**但大多数接口是分角色的：**
- **H5 用户端**：浏览商家、下单、支付、评价、个人中心
- **商家端**：订单管理、菜品管理、营业状态
- **管理台**：用户管理、商家审核、订单管理

所以大部分接口只被一个前端使用，少数共用接口（如登录、文件上传）才需要改多处。

**三个前端对同一个 API 使用方式不一致，谁的责任：**

**场景：比如获取订单列表接口，H5 用分页，商家端不用分页，管理台用另一种分页**

**责任划分：**

1. **后端责任：**
   - API 设计要考虑通用性，不要为某个前端定制
   - 提供统一的、灵活的参数（如都支持分页）
   - 保证 API 文档清晰、完整

2. **前端责任：**
   - 按照 API 文档正确使用接口
   - 不要依赖后端的"容错"或"未文档化的行为"
   - 三个前端之间应该统一 API 调用方式（可以封装公共 API 层）

3. **共同责任：**
   - 前后端一起制定 API 规范
   - 有变更时及时沟通

**为什么会出现使用方式不一致：**

1. **不同时间开发**：
   - H5 先做，接口是一个样
   - 商家端后做，加了新参数，老参数没改
   - 管理台最后做，又加了新参数
   - 接口越来越乱

2. **不同的人开发**：
   - 三个前端是不同的人写的
   - 每个人理解不一样，调用方式也不一样

3. **后端没有统一规划**：
   - 前端要什么就加什么，不考虑整体设计
   - 结果接口参数越来越多，越来越乱

**怎么改善：**

1. **API 设计先行**：
   - 写接口前先设计，评审通过再开发
   - 考虑所有使用场景，不要加了又改

2. **统一 API 调用层**：
   - 三个前端虽然独立，但可以抽一个公共的 API SDK（npm 包）
   - 封装接口调用、参数处理、错误处理
   - 保证三个端调用方式一致

3. **API 版本管理**：
   - 接口大改时，用 v1、v2 区分
   - 不要在原有接口上不断加参数

4. **前后端联调规范**：
   - 接口变更要通知所有相关前端
   - 有变更日志

**当前项目的情况：**
- 三个前端都是 Vue + JS，但代码是独立的
- API 调用分别封装在各自的 `api/index.js` 里
- 很可能有重复和不一致的地方
- 建议：可以考虑抽一个公共的 API 类型定义（TypeScript interface），保证前后端和三个前端的一致性

---

### 118. 前端访问后端时，CORS 配置使用 allowedOriginPatterns("*") 允许所有来源，这在开发环境很方便。但三个前端分别运行在 localhost:3001（H5）、localhost:3002（商家）、localhost:3003（管理台），是否应该针对这三个具体域名做 CORS 白名单以达到最小的安全攻击面？生产环境下，这三个前端会部署到不同域名还是同一域名（子域名）？

**回答：**

**开发环境应该用白名单吗：**
- 开发环境用 `*` 问题不大，反正都是 localhost
- 改成白名单也可以，更规范一点
- 但开发环境安全风险低，优先级不高

**生产环境绝对不能用 `*` + `allowCredentials(true)`**
- 注意：当前配置是 `allowedOriginPatterns("*")` + `allowCredentials(true)`
- 这是有问题的！因为浏览器安全策略规定：
  - 如果 `Access-Control-Allow-Credentials: true`
  - 那么 `Access-Control-Allow-Origin` 不能是 `*`
  - 必须是具体的域名
- 但 Spring 的 `allowedOriginPatterns("*")` 其实会根据请求动态返回具体的 Origin，不是真的返回 `*`
- 所以功能上没问题，但安全上还是有风险

**生产环境用 `*` 的安全风险：**

1. **CSRF 攻击**：
   - 任何网站都可以向你的 API 发请求（带 cookie）
   - 攻击者的网站可以诱导已登录用户访问，执行操作
   - 比如：用户登录了外卖网站，然后访问攻击者的网站，攻击者的网站自动发请求"修改用户密码"

2. **数据泄露**：
   - 恶意网站可以通过 CORS 读取用户的敏感数据
   - 比如获取用户的订单列表、地址等

3. **钓鱼攻击**：
   - 仿冒网站可以直接调用你的 API
   - 用户在仿冒网站输入信息，直接提交到你的后端

**生产环境应该怎么配：**

**如果三个前端在不同域名：**
```java
registry.addMapping("/**")
    .allowedOrigins("https://h5.takeout.com", "https://merchant.takeout.com", "https://admin.takeout.com")
    .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
    .allowedHeaders("*")
    .allowCredentials(true)
    .maxAge(3600);
```

**如果三个前端在同一个域名的不同路径：**
- 比如 `www.takeout.com/h5`、`www.takeout.com/merchant`、`www.takeout.com/admin`
- 那根本不需要 CORS（同源）
- 直接同域部署就行，最安全

**生产环境下，三个前端通常怎么部署：**

**方案一：子域名（最常见）**
- `www.takeout.com` 或 `h5.takeout.com` → H5 用户端
- `merchant.takeout.com` → 商家端
- `admin.takeout.com` → 管理台
- 优点：清晰、独立部署、CDN 配置方便
- 缺点：需要配置 CORS

**方案二：同域名不同路径**
- `www.takeout.com/` → H5
- `www.takeout.com/merchant/` → 商家端
- `www.takeout.com/admin/` → 管理台
- 优点：不需要 CORS、共享 cookie
- 缺点：部署相对麻烦，Nginx 要配置多个 location

**方案三：完全独立域名**
- 三个完全不同的域名（如 xxx.com、yyy.com、zzz.com）
- 不推荐，维护成本高

**当前项目的情况：**
- 开发环境三个不同端口（3001/3002/3003），模拟三个独立服务
- 生产环境大概率用**子域名**方案
- CORS 白名单要配置三个子域名

**建议：**
- 开发环境：继续用 `*`，方便
- 生产环境：改成具体的域名白名单
- 可以通过配置文件控制：
  ```yaml
  # application-dev.yml
  cors:
    allowed-origins: "*"
  
  # application-prod.yml
  cors:
    allowed-origins:
      - https://h5.takeout.com
      - https://merchant.takeout.com
      - https://admin.takeout.com
  ```

---

## 二十八、数据建模与字段设计

### 119. t_merchant.score 使用 DECIMAL(3,1)，字面意义最大值为 99.9。但评分系统的逻辑最大值是 5.0，DB 层允许存入 99.9，如果代码 Bug 导致 avgScoreByMerchant 计算异常时传入了 100 分，DB 会截断还是报错？DECIMAL(2,1) 最大只能存 9.9，为什么不用 DECIMAL(2,1) 或者直接用 TINYINT（0-50 表示 0.0-5.0）？

**回答：**

**传入 100 分，DB 会怎样：**

**`DECIMAL(3,1)` 的含义：**
- 总共 3 位数字，其中 1 位小数
- 整数部分 2 位，小数部分 1 位
- 范围：`-99.9 ~ 99.9`

**插入 100 会怎样：**
- **会报错**，不是截断
- MySQL 会抛出 `Out of range value for column 'score'` 错误
- 因为 100 有 3 位整数 + 0 位小数 = 3 位，但 `DECIMAL(3,1)` 整数位只能有 2 位
- 严格模式下（默认）直接报错，非严格模式下可能截断为 99.9（但不推荐）

**为什么不用 DECIMAL(2,1)：**
- `DECIMAL(2,1)` 范围是 `-9.9 ~ 9.9`
- 存评分 0-5 完全够用
- 可能是开发者随手写了 `(3,1)`，没想那么多
- 或者预留了空间，担心以后评分系统变了（比如 0-10 分制）

**为什么不用 TINYINT（0-50 表示 0.0-5.0）：**

**用 TINYINT 的好处：**
- 存储空间更小：TINYINT 1 字节，DECIMAL(3,1) 也是 2 字节左右，差别不大
- 计算更快：整数运算比小数快
- 精度问题：浮点数有精度问题，整数没有

**用 TINYINT 的坏处：**
- 可读性差：看到 43 不知道是 4.3 分（得除以 10）
- 容易搞错：乘除 10 的地方容易写错
- 业务含义不直观：数据库里存的不是真实的评分值

**为什么实际项目中还是用 DECIMAL 多：**
- 直观：存的就是 4.5，一看就懂
- 计算方便：直接 AVG() 就行，不用来回转换
- 不会因为"忘了除以 10"而出 bug

**当前设计的问题：**
- `DECIMAL(3,1)` 精度浪费（存 0-5 不需要 99.9）
- 但问题不大，不影响功能
- 属于"设计不够精准但能用"的范畴

**评分字段的最佳实践：**
- 如果评分范围固定（如 0-5、0-10），用 `DECIMAL(2,1)` 就够了
- 如果是更复杂的评分（如百分制），用 `DECIMAL(5,2)`
- 数据量特别大（上亿条）、对性能敏感，可以考虑用 TINYINT/SMALLINT

**顺便说：当前 merchant.score 更新了吗？**
- 从代码来看，`merchant.score` 字段存在，但**似乎没有更新逻辑**
- 评价模块（ReviewService）提交评价后，有没有更新商家的平均分？
- 如果没有，那 score 字段就是个摆设，一直是初始值
- 这是个更大的问题（比字段类型问题严重多了）

---

### 120. t_order 表的 longitude 和 latitude 使用 DECIMAL 类型，精度不明。MyBatis-Plus 映射为 BigDecimal。但 Merchant 同样有经纬度，使用的是 BigDecimal。如果后续要基于地理坐标做距离排序（ST_Distance_Sphere），DECIMAL 的精度和索引设计是否满足？为什么没有在 longitude 和 latitude 上加复合索引？

**回答：**

**DECIMAL 精度够吗：**

**经纬度需要多少精度：**
- 经度范围：-180 ~ 180
- 纬度范围：-90 ~ 90
- 精度要求：
  - 小数点后 4 位：~11 米精度
  - 小数点后 5 位：~1.1 米精度
  - 小数点后 6 位：~0.11 米精度

**外卖系统需要多少精度：**
- 外卖配送，精度到"米"级就够了
- 小数点后 6 位完全够用
- 所以 `DECIMAL(10,6)` 或 `DECIMAL(9,6)` 比较合适

**当前 DECIMAL 的精度：**
- 题目说"精度不明"——需要看具体建表 SQL
- 如果是 `DECIMAL(10,6)` 就合适
- 如果精度太低（如只有 2 位小数），距离计算会有误差

**MySQL 的 ST_Distance_Sphere 对类型有要求吗：**
- `ST_Distance_Sphere()` 接受 POINT 类型参数
- POINT 内部用 DOUBLE 存储坐标
- 所以 DECIMAL 传进去会被转成 DOUBLE
- 精度上 DECIMAL 转 DOUBLE 没问题（DOUBLE 有 15 位有效数字）

**为什么没有加复合索引：**

**普通的 (longitude, latitude) 复合索引有用吗：**
- 对于 `WHERE lat BETWEEN ? AND ? AND lng BETWEEN ? AND ?` 这样的查询
- 复合索引能加速，但效果有限（范围查询的第二列不太好用到索引）
- 因为经纬度是二维范围查询，普通 B-Tree 索引效率不高

**真正的地理空间查询需要什么索引：**
- **空间索引（SPATIAL INDEX）**
- MySQL 支持空间索引（MyISAM 原生支持，InnoDB 5.7+ 也支持）
- 空间索引基于 R-Tree 结构，专门优化地理空间查询
- 比普通复合索引快很多

**为什么没加：**

1. **字段类型不对**：
   - 要加空间索引，字段类型必须是 `POINT`（或其他地理类型）
   - 当前是两个 DECIMAL 字段，加不了空间索引
   - 只能加普通的复合索引

2. **数据量小**：
   - 商家数量不多（几千个），全表扫描也能接受
   - 没必要加索引
   - 但如果量大了就会慢

3. **当前只用了 ST_Distance_Sphere 计算距离，没有空间过滤**：
   - `MerchantService.nearby()` 的 SQL 是怎样的？
   - 如果是 `SELECT * FROM merchant ORDER BY ST_Distance_Sphere(...) LIMIT 10`
   - 那是全表计算距离后排序，索引没用
   - 应该先加空间范围过滤（MBRContains），再计算距离排序

**怎么优化地理查询：**

**方案一：加普通复合索引 + 范围过滤**
```sql
-- 先按经纬度范围过滤（矩形范围）
WHERE lat BETWEEN ? AND ?
  AND lng BETWEEN ? AND ?
ORDER BY ST_Distance_Sphere(...)
LIMIT 10
```
- 加 `(lat, lng)` 复合索引
- 先过滤出矩形范围内的商家，再计算距离排序
- 性能比全表计算好很多

**方案二：改用 POINT 类型 + 空间索引（推荐）**
```sql
-- 建表时用 POINT 类型
location POINT NOT NULL SRID 4326,
SPATIAL INDEX idx_location(location)

-- 查询时
SELECT * FROM merchant
WHERE ST_Distance_Sphere(location, ST_SRID(POINT(?, ?), 4326)) < 5000
ORDER BY ST_Distance_Sphere(location, ST_SRID(POINT(?, ?), 4326))
LIMIT 10
```
- 空间索引 + 空间函数，性能最好
- 但代码改动稍大（要处理 POINT 类型）

**方案三：用 Redis Geo**
- 把商家位置存在 Redis Geo 中
- 查询附近商家直接用 Redis
- 性能最好，减轻 DB 压力
- 但要维护 Redis 和 DB 的数据一致性

**当前项目的情况：**
- 用了 `ST_Distance_Sphere`（说明 MySQL 支持空间函数）
- 但字段是两个 DECIMAL，不是 POINT 类型
- 没有空间索引
- 商家数量少的话没问题，量大了需要优化