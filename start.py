#!/usr/bin/env python3
"""一键启动（不改系统环境变量）

1. 找本机 JDK（优先 JAVA_HOME，其次常见安装路径）
2. 项目内 tools/ 没有 Maven 就自动下载解压（仓库里不放二进制）
3. 用 Maven 打成可执行 jar
4. 启动 SpringBoot，默认 http://127.0.0.1:8003/

用法：
    python start.py                # 构建并启动
    python start.py --build-only   # 只打包，不启动
"""
import os
import shutil
import subprocess
import sys
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent
TOOLS = ROOT / "tools"
MVN_DIR = TOOLS / "apache-maven-3.9.9"
MAVEN_URL = ("https://repo.maven.apache.org/maven2/org/apache/maven/"
             "apache-maven/3.9.9/apache-maven-3.9.9-bin.zip")
IS_WIN = os.name == "nt"
JAVA_EXE = "java.exe" if IS_WIN else "java"


def find_java_home():
    candidates = [
        os.environ.get("JAVA_HOME"),
        r"C:\Program Files\Java\jdk-17",
        r"C:\Program Files\Eclipse Adoptium\jdk-17",
        r"D:\jdk-26.0.2.1",
        r"D:\jdk-17",
        "/usr/lib/jvm/java-17-openjdk",
    ]
    for c in candidates:
        if c and (Path(c) / "bin" / JAVA_EXE).exists():
            return str(Path(c))
    found = shutil.which("java")
    if found:
        return str(Path(found).resolve().parent.parent)
    raise SystemExit("找不到 JDK 17+，请先安装 JDK 并设置 JAVA_HOME")


def mvn_command():
    if IS_WIN:
        return ["cmd", "/c", str(MVN_DIR / "bin" / "mvn.cmd")]
    return [str(MVN_DIR / "bin" / "mvn")]


def ensure_maven():
    target = MVN_DIR / "bin" / ("mvn.cmd" if IS_WIN else "mvn")
    if target.exists():
        return
    TOOLS.mkdir(parents=True, exist_ok=True)
    archive = TOOLS / "maven.zip"
    print("[1/3] 首次运行：下载 Maven 3.9.9（约 9MB）…", flush=True)
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    with opener.open(MAVEN_URL, timeout=180) as resp, open(archive, "wb") as f:
        shutil.copyfileobj(resp, f)
    with zipfile.ZipFile(archive) as z:
        z.extractall(TOOLS)
    archive.unlink(missing_ok=True)
    if not target.exists():
        raise SystemExit("Maven 解压后未找到可执行文件：" + str(target))
    print("      Maven 就绪（仅放在项目 tools/ 目录）", flush=True)


def main():
    java_home = find_java_home()
    print("JDK  :", java_home, flush=True)
    ensure_maven()

    env = dict(os.environ)
    env["JAVA_HOME"] = java_home
    env["PYTHONIOENCODING"] = "utf-8"
    env.setdefault("BLOG_PORT", "8003")

    print("[2/3] 打包 SpringBoot 可执行 jar …", flush=True)
    subprocess.run(mvn_command() + ["-B", "-q", "-DskipTests", "package"],
                   cwd=ROOT, env=env, check=True)

    jar = ROOT / "target" / "blog-app.jar"
    if not jar.exists():
        raise SystemExit("打包完成但未找到 target/blog-app.jar")

    if "--build-only" in sys.argv:
        print("完成：", jar, flush=True)
        return

    print("[3/3] 启动服务： http://127.0.0.1:%s/" % env["BLOG_PORT"], flush=True)
    java = str(Path(java_home) / "bin" / JAVA_EXE)
    # 命令行参数优先级最高：避免宿主环境里的 SERVER__PORT 之类变量把端口改掉
    subprocess.run([java, "-jar", str(jar), "--server.port=" + env["BLOG_PORT"]],
                   cwd=ROOT, env=env, check=True)


if __name__ == "__main__":
    main()
