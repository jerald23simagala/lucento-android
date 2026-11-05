"""Asset research helpers. Downloads go to the directory given on the command line; nothing here executes fetched data.

  research.py play <package> <gl> <outdir>         Play listing -> title, developer, icon URL; saves app-icon.png (512px)
  research.py playsearch <gl> <query...>           package ids in Play search results
  research.py commons <query...>                   Commons file search (namespace 6)
  research.py commonsinfo <File:Title> <outdir>    url + licence metadata; saves the original file
  research.py get <url> <outfile>                  plain download
  research.py imgs <url>                           logo-ish <img>/<svg> candidates on a page
"""
import html
import io
import json
import os
import re
import sys
import urllib.parse
import urllib.request

BROWSER_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36"
WIKI_UA = "LucentoAssetResearch/1.0 (institution logo research; contact via project maintainer)"


def fetch(url, ua=BROWSER_UA, timeout=40):
    req = urllib.request.Request(url, headers={"User-Agent": ua, "Accept-Language": "en"})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return r.read(), r.headers.get("Content-Type", ""), r.geturl()


def play(pkg, gl, outdir):
    url = f"https://play.google.com/store/apps/details?id={pkg}&hl=en&gl={gl}"
    body, _, _ = fetch(url)
    text = body.decode("utf-8", "replace")
    title = re.search(r'<h1[^>]*>(?:<span[^>]*>)?([^<]+)', text)
    dev = re.search(r'href="/store/apps/dev(?:eloper)?\?id=[^"]+"[^>]*>(?:<span[^>]*>)?([^<]+)', text)
    icon = re.search(r'<img[^>]+src="(https://play-lh\.googleusercontent\.com/[^"=]+)[^"]*"[^>]*alt="Icon image"', text) \
        or re.search(r'<meta property="og:image" content="(https://play-lh\.googleusercontent\.com/[^"=]+)', text)
    info = {"package": pkg, "listing": url, "title": html.unescape(title.group(1)).strip() if title else None,
            "developer": html.unescape(dev.group(1)).strip() if dev else None,
            "icon_url": icon.group(1) + "=s512" if icon else None}
    print(json.dumps(info, ensure_ascii=False))
    if icon and outdir:
        from PIL import Image
        raw, ctype, _ = fetch(info["icon_url"])
        os.makedirs(outdir, exist_ok=True)
        with Image.open(io.BytesIO(raw)) as im:
            fmt = im.format
            im.convert("RGBA").save(os.path.join(outdir, "app-icon.png"), "PNG")
        info["served_format"] = fmt
        with open(os.path.join(outdir, "play.json"), "w", encoding="utf-8") as f:
            json.dump(info, f, ensure_ascii=False, indent=1)
        print("saved app-icon.png (served", fmt, ")")


def playsearch(gl, query):
    url = "https://play.google.com/store/search?" + urllib.parse.urlencode({"q": query, "c": "apps", "hl": "en", "gl": gl})
    text = fetch(url)[0].decode("utf-8", "replace")
    seen = []
    for m in re.findall(r'details\?id=([A-Za-z0-9._]+)', text):
        if m not in seen:
            seen.append(m)
    print(" ".join(seen[:15]))


def commons(query):
    url = "https://commons.wikimedia.org/w/api.php?" + urllib.parse.urlencode(
        {"action": "query", "list": "search", "srsearch": query, "srnamespace": 6, "format": "json", "srlimit": 12})
    d = json.loads(fetch(url, WIKI_UA)[0])
    for r in d["query"]["search"]:
        print(r["title"])


def commonsinfo(title, outdir):
    url = "https://commons.wikimedia.org/w/api.php?" + urllib.parse.urlencode(
        {"action": "query", "titles": title, "prop": "imageinfo", "iiprop": "url|extmetadata|size|mime", "format": "json"})
    d = json.loads(fetch(url, WIKI_UA)[0])
    page = next(iter(d["query"]["pages"].values()))
    ii = page["imageinfo"][0]
    meta = {k: re.sub(r"<[^>]+>", "", str(v.get("value", ""))).strip()[:200] for k, v in ii.get("extmetadata", {}).items()
            if k in ("LicenseShortName", "License", "Artist", "ImageDescription", "DateTimeOriginal", "Credit", "UsageTerms", "Copyrighted")}
    info = {"title": title, "url": ii["url"], "page": ii.get("descriptionurl"), "mime": ii.get("mime"),
            "size": [ii.get("width"), ii.get("height")], "meta": meta}
    print(json.dumps(info, ensure_ascii=False, indent=1))
    if outdir:
        os.makedirs(outdir, exist_ok=True)
        raw = fetch(ii["url"], WIKI_UA)[0]
        ext = os.path.splitext(urllib.parse.urlparse(ii["url"]).path)[1]
        with open(os.path.join(outdir, "commons" + ext), "wb") as f:
            f.write(raw)
        with open(os.path.join(outdir, "commons.json"), "w", encoding="utf-8") as f:
            json.dump(info, f, ensure_ascii=False, indent=1)


def get(url, out):
    raw, ctype, final = fetch(url)
    os.makedirs(os.path.dirname(out) or ".", exist_ok=True)
    with open(out, "wb") as f:
        f.write(raw)
    print(len(raw), ctype, final)


def imgs(url):
    raw, _, final = fetch(url)
    text = raw.decode("utf-8", "replace")
    for m in re.findall(r'<img[^>]+>', text):
        src = re.search(r'(?:src|data-src)="([^"]+)"', m)
        if src and re.search(r'logo|brand|header', m, re.I):
            print("IMG", urllib.parse.urljoin(final, html.unescape(src.group(1))), "|", (re.search(r'alt="([^"]*)"', m) or [None, ""])[1][:40])
    for m in re.findall(r'(?:href|src|content)="([^"]+\.svg[^"]*)"', text):
        print("SVG", urllib.parse.urljoin(final, html.unescape(m)))
    print("inline <svg> count:", text.count("<svg"), "| final:", final)


if __name__ == "__main__":
    cmd, args = sys.argv[1], sys.argv[2:]
    if cmd == "play":
        play(args[0], args[1], args[2] if len(args) > 2 else None)
    elif cmd == "playsearch":
        playsearch(args[0], " ".join(args[1:]))
    elif cmd == "commons":
        commons(" ".join(args))
    elif cmd == "commonsinfo":
        commonsinfo(args[0], args[1] if len(args) > 1 else None)
    elif cmd == "get":
        get(args[0], args[1])
    elif cmd == "imgs":
        imgs(args[0])
