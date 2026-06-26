const { chromium } = require('playwright');
(async () => {
  const browser = await chromium.launch({ headless: true });
  const ctx = await browser.newContext({ viewport: { width: 390, height: 844 } });
  const page = await ctx.newPage();
  
  // 登录页
  await page.goto('http://localhost:3001/login');
  await page.waitForTimeout(1500);
  await page.screenshot({ path: 'ss_login.png', fullPage: false });
  console.log('login done');

  // 首页（先注入 token 绕过登录）
  await ctx.addCookies([]);
  await page.evaluate(() => {
    localStorage.setItem('token', 'fake-token-for-screenshot');
  });
  await page.goto('http://localhost:3001/');
  await page.waitForTimeout(2000);
  await page.screenshot({ path: 'ss_home.png', fullPage: false });
  console.log('home done');

  // 订单页
  await page.goto('http://localhost:3001/orders');
  await page.waitForTimeout(1500);
  await page.screenshot({ path: 'ss_orders.png', fullPage: false });
  console.log('orders done');

  // 我的页
  await page.goto('http://localhost:3001/profile');
  await page.waitForTimeout(1500);
  await page.screenshot({ path: 'ss_profile.png', fullPage: false });
  console.log('profile done');

  // 搜索页
  await page.goto('http://localhost:3001/search');
  await page.waitForTimeout(1200);
  await page.screenshot({ path: 'ss_search.png', fullPage: false });
  console.log('search done');

  await browser.close();
  console.log('ALL DONE');
})();
