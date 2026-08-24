#!/usr/bin/env python3
"""Tiny static file server for the Sunday School lesson downloads."""
import http.server, socketserver, os, functools

PORT = int(os.environ.get("PORT", "8000"))
DIR = os.path.dirname(os.path.abspath(__file__))

FILES = [
    ("sunday-school-marriage.pdf", "PDF", "Formatted, print-ready A4 booklet (4 pages)"),
    ("sunday-school-marriage.md", "Markdown", "Headings, lists and emphasis in Markdown source"),
    ("sunday-school-marriage.txt", "Plain text", "Clean ASCII, fixed-width \u2014 opens anywhere"),
]

def card(name, label, desc):
    size = os.path.getsize(os.path.join(DIR, name)) / 1024
    return f"""
    <a class="card" href="{name}" download>
      <div class="tag">{label}</div>
      <div class="meta">
        <div class="fname">{name}</div>
        <div class="desc">{desc}</div>
        <div class="size">{size:.0f} KB</div>
      </div>
      <div class="dl">Download &darr;</div>
    </a>"""

PAGE = """<!doctype html>
<html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Sunday School &middot; Marriage &mdash; Downloads</title>
<style>
 :root{{--ink:#1a1a1a;--accent:#7a1f2b;--muted:#6b6b6b;--rule:#e3d9cc;--bg:#faf7f2}}
 *{{box-sizing:border-box}}
 body{{margin:0;background:var(--bg);color:var(--ink);
   font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,sans-serif;
   display:flex;justify-content:center;padding:48px 20px}}
 .wrap{{width:100%;max-width:660px}}
 .kicker{{letter-spacing:.28em;font-size:11px;color:var(--muted);text-transform:uppercase;text-align:center}}
 h1{{font-family:Georgia,"Times New Roman",serif;color:var(--accent);
   font-size:44px;margin:6px 0 4px;text-align:center;font-weight:700}}
 .sub{{text-align:center;color:var(--muted);font-style:italic;
   font-family:Georgia,serif;font-size:15px;margin-bottom:8px}}
 hr{{border:0;border-top:1px solid var(--rule);width:120px;margin:18px auto 34px}}
 .card{{display:flex;align-items:center;gap:18px;text-decoration:none;color:inherit;
   background:#fff;border:1px solid var(--rule);border-radius:14px;padding:18px 20px;
   margin-bottom:14px;transition:.18s;box-shadow:0 1px 2px rgba(0,0,0,.04)}}
 .card:hover{{border-color:var(--accent);transform:translateY(-2px);
   box-shadow:0 8px 22px rgba(122,31,43,.13)}}
 .tag{{flex:0 0 92px;text-align:center;font-size:11px;font-weight:700;letter-spacing:.08em;
   text-transform:uppercase;color:#fff;background:var(--accent);border-radius:8px;padding:10px 6px}}
 .meta{{flex:1;min-width:0}}
 .fname{{font-weight:600;font-size:15px;word-break:break-all}}
 .desc{{color:var(--muted);font-size:13px;margin-top:3px}}
 .size{{color:#9a9a9a;font-size:12px;margin-top:4px}}
 .dl{{flex:0 0 auto;font-size:13px;font-weight:600;color:var(--accent);white-space:nowrap}}
 .note{{text-align:center;color:var(--muted);font-size:12.5px;margin-top:26px;line-height:1.6}}
 @media(max-width:520px){{.card{{flex-wrap:wrap}}.dl{{width:100%;text-align:right}}}}
</style></head><body><div class="wrap">
 <div class="kicker">Sunday School</div>
 <h1>Marriage</h1>
 <div class="sub">What God instituted in the beginning &mdash; a study in Genesis 1 and 2</div>
 <hr>
 {cards}
 <div class="note">Click any card to download.<br>
 All three files live in the <code>sunday-school/</code> folder of the repository.</div>
</div></body></html>"""

class H(http.server.SimpleHTTPRequestHandler):
    def do_GET(self):
        if self.path in ("/", "/index.html"):
            html = PAGE.format(cards="\n".join(card(*f) for f in FILES)).encode()
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(html)))
            self.end_headers()
            self.wfile.write(html)
            return
        super().do_GET()

    def end_headers(self):
        self.send_header("Access-Control-Allow-Origin", "*")
        super().end_headers()

handler = functools.partial(H, directory=DIR)
socketserver.TCPServer.allow_reuse_address = True
with socketserver.TCPServer(("0.0.0.0", PORT), handler) as httpd:
    print(f"Serving downloads on 0.0.0.0:{PORT}")
    httpd.serve_forever()
