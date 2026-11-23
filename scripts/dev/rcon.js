#!/usr/bin/env node
/**
 * 极简 RCON 客户端（Minecraft RCON 协议，零依赖；本机自动化用）。
 *
 * 用途：从 docker 网络内部（或任何能到 RCON 端口的地方）向服务器发控制台命令并取回输出，
 * 例如 `/config im status` —— issue #616 的现场取证需要它。
 *
 * 用法：
 *   node rcon.js <host> <port> <password> "<command>"
 *
 * 协议：TCP，包体 = [length(int32 LE)][id(int32 LE)][type(int32 LE)][body(ASCIIZ)][0x00]
 *   type 3 = AUTH（失败时 id = -1），type 2 = COMMAND；服务端可能把长输出拆成多个包。
 */

const net = require("net");

const [, , host, portArg, password, ...cmdParts] = process.argv;
const command = cmdParts.join(" ");
if (!host || !portArg || !password || !command) {
    console.error('用法: node rcon.js <host> <port> <password> "<command>"');
    process.exit(2);
}
const port = Number(portArg);

const AUTH = 3;
const COMMAND = 2;

function packet(id, type, body) {
    const payload = Buffer.concat([Buffer.from(body, "utf8"), Buffer.from([0, 0])]);
    const buf = Buffer.alloc(4 + 4 + 4 + payload.length);
    buf.writeInt32LE(4 + 4 + payload.length, 0);
    buf.writeInt32LE(id, 4);
    buf.writeInt32LE(type, 8);
    payload.copy(buf, 12);
    return buf;
}

let buffer = Buffer.alloc(0);
let finishTimer = null;
const outputs = [];
let authed = false;

const socket = net.connect({ host, port }, () => {
    socket.write(packet(1, AUTH, password));
});

socket.on("data", (chunk) => {
    buffer = Buffer.concat([buffer, chunk]);
    while (buffer.length >= 4) {
        const len = buffer.readInt32LE(0);
        if (buffer.length < 4 + len) break;
        const body = buffer.subarray(4, 4 + len);
        const id = body.readInt32LE(0);
        const type = body.readInt32LE(4);
        const text = body.subarray(8, body.length - 2).toString("utf8");
        buffer = buffer.subarray(4 + len);

        // Paper/Spigot 对成功鉴权的回包用的是 type=2（不是协议里的 3），所以以 id 判定：
        // 鉴权失败时 id 固定为 -1；成功则回显我们发出的 id（1）。
        if (!authed) {
            if (id === -1) {
                console.error("AUTH 失败（密码错误）");
                socket.destroy();
                process.exit(3);
            }
            authed = true;
            socket.write(packet(2, COMMAND, command));
            continue;
        }
        outputs.push(text);
        // 多行输出会被拆成多个包：收到首包后再等一小会儿收齐，然后收尾退出
        clearTimeout(finishTimer);
        finishTimer = setTimeout(finish, 400);
    }
});

socket.on("error", (e) => {
    console.error("连接错误:", e.message);
    process.exit(4);
});

function finish() {
    console.log(outputs.join("").replace(/\r/g, ""));
    socket.destroy();
    process.exit(0);
}

socket.on("close", () => {
    if (!authed) {
        console.error("连接在鉴权前关闭");
        process.exit(5);
    }
    finish();
});

setTimeout(() => {
    console.error("超时（无回包）");
    socket.destroy();
    process.exit(6);
}, 15000);
