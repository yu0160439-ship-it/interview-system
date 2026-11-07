// Pages Functions：把 /api/* 同源反代到外部后端（Render），前端保持相对路径、无需跨域。
// 后端地址通过 Pages 环境变量 BACKEND_URL 配置，例如 https://interview-system.onrender.com
// 透传 SSE 流式响应，不做缓冲。

export async function onRequest(context) {
  const backend = (context.env.BACKEND_URL || '').replace(/\/+$/, '');
  if (!backend) {
    return new Response(JSON.stringify({ message: 'BACKEND_URL 未配置' }), {
      status: 502,
      headers: { 'Content-Type': 'application/json; charset=utf-8' },
    });
  }

  const request = context.request;
  const url = new URL(request.url);
  const target = backend + url.pathname + url.search;

  const headers = new Headers(request.headers);
  headers.set('host', new URL(backend).host);

  const init = {
    method: request.method,
    headers,
    redirect: 'follow',
  };
  if (request.method !== 'GET' && request.method !== 'HEAD') {
    init.body = request.body;
  }

  let resp;
  try {
    resp = await fetch(target, init);
  } catch (err) {
    return new Response(JSON.stringify({ message: '后端连接失败: ' + (err && err.message) }), {
      status: 502,
      headers: { 'Content-Type': 'application/json; charset=utf-8' },
    });
  }

  const outHeaders = new Headers(resp.headers);
  // 让边缘层按透传后的实际内容重算编码与长度，避免压缩头与已解压 body 不一致
  outHeaders.delete('content-encoding');
  outHeaders.delete('content-length');

  return new Response(resp.body, {
    status: resp.status,
    statusText: resp.statusText,
    headers: outHeaders,
  });
}
