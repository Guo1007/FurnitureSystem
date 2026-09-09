#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
可视化 Sniffer —— HTTPS 加密验证工具
====================================
用途：抓取指定主机的网络包，在界面中查看报文内容，对比
  - 443 端口（HTTPS）：内容是乱码/密文
  - 80 端口（HTTP）  ：内容是明文
从而直观验证 HTTPS 传输加密是否生效。

运行前提：
  1. 已安装 Python 3.9+，并执行:  python -m pip install -r requirements.txt
  2. Windows 上需安装 Npcap 网卡驱动:  https://npcap.com/#download
  3. 必须以『管理员权限』运行（抓原始报文需要）

用法：
  在目标主机输入框填网址（如 mingcheng.asia），点【开始监听】；
  用浏览器访问该网址，包会实时出现在表格里；
  点某一行，下方显示 十六进制+ASCII 内容。
"""
import threading
import time
import tkinter as tk
from tkinter import ttk, scrolledtext, messagebox
import socket

try:
    from scapy.all import sniff, Ether, IP, IPv6, TCP, UDP, Raw
except ImportError:
    raise SystemExit(
        "缺少 scapy，请先执行: python -m pip install -r requirements.txt"
    )


class SnifferApp:
    def __init__(self, root):
        self.root = root
        root.title("HTTPS 加密验证 Sniffer")
        root.geometry("900x620")

        # ---------- 顶栏：目标与过滤 ----------
        top = ttk.Frame(root, padding=6)
        top.pack(fill="x")

        ttk.Label(top, text="目标主机(域名或IP):").pack(side="left")
        self.host_var = tk.StringVar(value="mingcheng.asia")
        self.host_entry = ttk.Entry(top, textvariable=self.host_var, width=28)
        self.host_entry.pack(side="left", padx=4)

        ttk.Label(top, text="端口: (留空=全部)").pack(side="left", padx=(8, 0))
        self.port_var = tk.StringVar(value="")
        self.port_entry = ttk.Entry(top, textvariable=self.port_var, width=8)
        self.port_entry.pack(side="left", padx=4)

        self.start_btn = ttk.Button(top, text="开始监听", command=self.start)
        self.start_btn.pack(side="left", padx=6)
        self.stop_btn = ttk.Button(top, text="停止", command=self.stop, state="disabled")
        self.stop_btn.pack(side="left")
        self.clear_btn = ttk.Button(top, text="清空", command=self.clear)
        self.clear_btn.pack(side="left", padx=6)

        status = ttk.Frame(root, padding=(6, 0))
        status.pack(fill="x")
        self.status_var = tk.StringVar(value="状态: 未开始")
        ttk.Label(status, textvariable=self.status_var, foreground="#333").pack(side="left")

        # ---------- 包列表表格 ----------
        paned = ttk.PanedWindow(root, orient="vertical")
        paned.pack(fill="both", expand=True)

        frame_list = ttk.Frame(paned)
        paned.add(frame_list, weight=3)

        cols = ("idx", "time", "proto", "src", "sport", "dst", "dport", "len", "note")
        self.tree = ttk.Treeview(frame_list, columns=cols, show="headings")
        headers = {
            "idx": ("#", 36),
            "time": ("时间", 90),
            "proto": ("协议", 48),
            "src": ("源IP", 130),
            "sport": ("源端口", 60),
            "dst": ("目的IP", 130),
            "dport": ("目的端口", 60),
            "len": ("长度", 50),
            "note": ("提示", 120),
        }
        for c, (title, w) in headers.items():
            self.tree.heading(c, text=title)
            self.tree.column(c, width=w, anchor="w")
        vsb = ttk.Scrollbar(frame_list, orient="vertical", command=self.tree.yview)
        self.tree.configure(yscrollcommand=vsb.set)
        self.tree.grid(row=0, column=0, sticky="nsew")
        vsb.grid(row=0, column=1, sticky="ns")
        frame_list.rowconfigure(0, weight=1)
        frame_list.columnconfigure(0, weight=1)
        self.tree.bind("<<TreeviewSelect>>", self.on_select)

        # ---------- 内容详情 ----------
        frame_detail = ttk.Frame(paned)
        paned.add(frame_detail, weight=2)

        ttk.Label(frame_detail, text="点击上方数据包查看内容（hex + ASCII）:", padding=(2, 2)).pack(anchor="w")
        self.detail = scrolledtext.ScrolledText(frame_detail, height=12, font=("Consolas", 10), wrap="none")
        self.detail.pack(fill="both", expand=True)

        # ---------- 状态相关 ----------
        self.sniffer_thread = None
        self.stop_flag = threading.Event()
        self.capture_state = False
        self.packets = []          # 存完整包供查看
        self.target_ips = set()
        self.filter_ips = None     # 过滤规则封装

    # ---------- 过滤逻辑 ----------
    def compile_filter(self, host: str, port: str) -> "function | None":
        """构造一个匹配函数: given(pkt) -> bool。失败返回 None。"""
        host = host.strip()
        port = port.strip()

        # 解析端口
        port_int = None
        if port:
            if not port.isdigit():
                messagebox.showerror("端口无效", f"端口必须是数字: {port}")
                return None
            port_int = int(port)

        # 解析目标 -> IP 集合
        ips = set()
        if host:
            if is_valid_ip(host):
                ips.add(host)
            else:
                try:
                    for infos in socket.getaddrinfo(host, None):
                        ip = infos[4][0]
                        if is_valid_ip(ip):
                            ips.add(ip)
                except Exception as e:
                    messagebox.showerror("解析失败", f"无法解析 {host}: {e}")
                    return None
                if not ips:
                    messagebox.showerror("解析失败", f"{host} 未解析到 IP")
                    return None

        def match(pkt):
            # src/dst 端口
            sport = dport = None
            if pkt.haslayer(TCP):
                sport, dport = pkt[TCP].sport, pkt[TCP].dport
            elif pkt.haslayer(UDP):
                sport, dport = pkt[UDP].sport, pkt[UDP].dport

            if port_int is not None:
                if sport != port_int and dport != port_int:
                    return False

            if ips:  # 与目标IP做双向匹配（源或目的）
                src = pkt[IP].src if pkt.haslayer(IP) else None
                dst = pkt[IP].dst if pkt.haslayer(IP) else None
                if not (src in ips or dst in ips):
                    return False

            return True

        return match

    # ---------- 启动 / 停止 ----------
    def start(self):
        if self.capture_state:
            return
        host = self.host_var.get()
        port = self.port_var.get()
        matcher = self.compile_filter(host, port)
        if matcher is None:
            return

        self.stop_flag.clear()
        self.capture_state = True
        self.start_btn.config(state="disabled")
        self.stop_btn.config(state="normal")
        target = host or "(本机所有)"
        self.status_var.set(f"状态: 监听中  目标={target}  端口={port or '全部'}")
        self.detail.delete("1.0", "end")
        self.detail.insert("1.0", "点击上方数据包查看内容（hex + ASCII）...\n")

        self.sniffer_thread = threading.Thread(
            target=self._capture_loop, args=(matcher,), daemon=True
        )
        self.sniffer_thread.start()

    def stop(self):
        self.stop_flag.set()
        self.capture_state = False
        self.start_btn.config(state="normal")
        self.stop_btn.config(state="disabled")
        self.status_var.set("状态: 已停止")

    def clear(self):
        if self.capture_state:
            messagebox.showinfo("提示", "请先点击【停止】再清空。")
            return
        self.tree.delete(*self.tree.get_children())
        self.detail.delete("1.0", "end")
        self.packets.clear()

    # ---------- 抓包循环（后台线程） ----------
    def _capture_loop(self, matcher):
        try:
            sniff(
                prn=lambda pkt: self._on_packet(pkt, matcher),
                store=False,
                stop_filter=lambda _p: self.stop_flag.is_set(),
            )
        except PermissionError:
            self._show_error("权限不足：请以『管理员』身份重新运行本程序。")
        except Exception as e:
            self._show_error(f"抓包出错: {e}")

    def _show_error(self, msg):
        self.root.after(0, lambda: messagebox.showerror("抓包失败", msg))
        self.root.after(0, self.stop)

    def _on_packet(self, pkt, matcher):
        if self.stop_flag.is_set():
            return
        try:
            if not matcher(pkt):
                return
            self.root.after(0, lambda: self._add_row(pkt))
        except Exception:
            pass  # 忽略解析异常，不影响抓包

    def _add_row(self, pkt):
        if not pkt.haslayer(IP):
            return
        src = pkt[IP].src
        dst = pkt[IP].dst
        sport = dport = "-"
        proto = "IP"
        if pkt.haslayer(TCP):
            proto = "TCP"
            sport, dport = pkt[TCP].sport, pkt[TCP].dport
        elif pkt.haslayer(UDP):
            proto = "UDP"
            sport, dport = pkt[UDP].sport, pkt[UDP].dport

        # 解析内容 + 提示（判断是否密文）
        payload = self._extract_payload(pkt)
        note = ""
        is_tls = (proto == "TCP" and (sport in (443, 8443) or dport in (443, 8443)))
        is_http = bool(re_http_match(payload))
        if is_tls:
            note = "⚠ HTTPS(密文)"
        elif is_http:
            note = "HTTP(明文)"
        elif payload:
            note = "有数据"

        idx = len(self.packets) + 1
        self.packets.append(pkt)
        self.tree.insert(
            "",
            "end",
            iid=str(idx),
            values=(
                idx,
                time.strftime("%H:%M:%S"),
                proto,
                src,
                sport,
                dst,
                dport,
                len(pkt),
                note,
            ),
        )
        # 只保留最新 300 条，防止界面卡死
        if len(self.packets) > 300:
            old = self.tree.get_children()[:-300]
            if old:
                self.tree.delete(*old)

    def _extract_payload(self, pkt):
        payload = b""
        for layer in (Raw,):
            if pkt.haslayer(layer):
                d = bytes(pkt[layer].load or b"")
                # 跳过明显非文本（减少乱码渲染）
                try:
                    d.decode("latin-1")
                    payload = d
                    break
                except Exception:
                    continue
        return payload

    # ---------- 查看内容 ----------
    def on_select(self, _event):
        sel = self.tree.selection()
        if not sel:
            return
        idx = int(sel[0])
        if idx - 1 >= len(self.packets):
            return
        pkt = self.packets[idx - 1]
        self.detail.delete("1.0", "end")
        self.detail.insert("1.0", self._format_packet(pkt))

    def _format_packet(self, pkt):
        raw = self._extract_payload(pkt)
        lines = []
        lines.append("═" * 60)
        if pkt.haslayer(Ether):
            lines.append(f"MAC   : {pkt[Ether].src} -> {pkt[Ether].dst}")
        if pkt.haslayer(IP):
            lines.append(f"IP    : {pkt[IP].src} -> {pkt[IP].dst}")
        if pkt.haslayer(TCP):
            t = pkt[TCP]
            lines.append(f"TCP   : :{t.sport} -> :{t.dport}   flags={t.flags!r}")
        elif pkt.haslayer(UDP):
            u = pkt[UDP]
            lines.append(f"UDP   : :{u.sport} -> :{u.dport}")
        lines.append(f"长度  : {len(pkt)} 字节")
        lines.append("")
        lines.append(hexdump(raw))
        return "\n".join(lines)


def is_valid_ip(s):
    try:
        socket.inet_pton(socket.AF_INET, s)
        return True
    except Exception:
        try:
            socket.inet_pton(socket.AF_INET6, s)
            return True
        except Exception:
            return False


def re_http_match(data: bytes) -> bool:
    """粗判是否为 HTTP 文本（含 GET/POST/HTTP/1.x 等关键字）"""
    try:
        head = data[:512].decode("latin-1", "ignore")
    except Exception:
        return False
    return any(k in head for k in ("HTTP/", "GET ", "POST ", "Host:", "Content-Type"))


def hexdump(data: bytes, width: int = 16) -> str:
    """十六进制 + ASCII 双栏展示。不可打印字符显示为 '.'。"""
    if not data:
        return "(无负载数据)"
    lines = []
    for off in range(0, len(data), width):
        chunk = data[off : off + width]
        hexpart = " ".join(f"{b:02X}" for b in chunk).ljust(width * 3)
        asc = "".join(chr(b) if 32 <= b < 127 else "." for b in chunk)
        lines.append(f"{off:06X}  {hexpart}  {asc}")
    return "\n".join(lines)


if __name__ == "__main__":
    root = tk.Tk()
    SnifferApp(root)
    root.mainloop()