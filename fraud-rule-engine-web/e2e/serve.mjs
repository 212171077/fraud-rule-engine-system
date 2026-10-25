import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { extname, resolve, sep } from 'node:path';

const root = resolve('dist/fraud-rule-engine-web/browser');
const types = { '.js': 'text/javascript', '.css': 'text/css', '.svg': 'image/svg+xml', '.ico': 'image/x-icon' };

createServer(async (request, response) => {
  const path = new URL(request.url, 'http://127.0.0.1:4300').pathname;
  if (path === '/app-config.json') {
    response.writeHead(200, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' });
    response.end('{"authMode":"local"}');
    return;
  }
  if (/^\/(api|actuator)(\/|$)/.test(path)) {
    response.writeHead(404);
    response.end();
    return;
  }
  const file = resolve(root, '.' + decodeURIComponent(path));
  if (!file.startsWith(root + sep)) {
    response.writeHead(400);
    response.end();
    return;
  }
  try {
    response.writeHead(200, { 'Content-Type': types[extname(file)] ?? 'text/html' });
    response.end(await readFile(extname(file) ? file : resolve(root, 'index.html')));
  } catch {
    response.writeHead(404);
    response.end();
  }
}).listen(4300, '127.0.0.1');
