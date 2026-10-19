/* 好友排行榜开放数据域：收主线程消息 → 拉好友云存储分数 → 画到共享画布 */
const sharedCanvas = wx.getSharedCanvas();
const ctx = sharedCanvas.getContext("2d");
let scoreKey = "score";

wx.onMessage(function (msg) {
  if (msg.type === "render") {
    scoreKey = msg.key || "score";
    sharedCanvas.width = msg.width || 600;
    sharedCanvas.height = msg.height || 800;
    render(msg.selfName || "");
  }
});

function render(selfName) {
  wx.getFriendCloudStorage({
    keyList: [scoreKey],
    success: function (res) {
      const list = (res.data || [])
        .map(function (u) { return { name: u.nickname, score: maxScore(u), avatar: u.avatarUrl }; })
        .filter(function (u) { return u.score > 0; })
        .sort(function (a, b) { return b.score - a.score; })
        .slice(0, 20);
      draw(list, selfName);
    },
    fail: function () { draw([], selfName); }
  });
}

function maxScore(u) {
  let best = 0;
  (u.KVDataList || []).forEach(function (kv) {
    const v = parseInt(kv.value, 10) || 0;
    if (v > best) best = v;
  });
  return best;
}

function draw(list, selfName) {
  const W = sharedCanvas.width, H = sharedCanvas.height;
  ctx.fillStyle = "#FFF6E3";
  ctx.fillRect(0, 0, W, H);
  ctx.fillStyle = "#0B3D2E";
  ctx.font = "bold 34px sans-serif";
  ctx.fillText("好友排行榜", 40, 70);
  if (!list.length) {
    ctx.font = "26px sans-serif";
    ctx.fillStyle = "#94a3b8";
    ctx.fillText("还没有好友记录，快去创造第一个吧！", 40, 160);
    return;
  }
  list.forEach(function (u, i) {
    const y = 130 + i * 60;
    if (u.name === selfName) {
      ctx.fillStyle = "rgba(245,158,11,0.25)";
      ctx.fillRect(30, y - 34, W - 60, 50);
    }
    ctx.fillStyle = "#0B3D2E";
    ctx.font = "28px sans-serif";
    ctx.fillText(String(i + 1) + ". " + u.name, 50, y);
    ctx.font = "26px sans-serif";
    ctx.fillStyle = "#B45309";
    ctx.fillText(String(u.score), W - 140, y);
  });
}
