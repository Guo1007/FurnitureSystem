#!/bin/bash
# 家具电商系统 - Docker 一键部署脚本
set -e

echo "=========================================="
echo "  家具电商系统 Docker 部署"
echo "=========================================="

# 检查 .env 文件
if [ ! -f .env ]; then
    echo "⚠️  未找到 .env 文件，正在从模板创建..."
    cp .env.example .env
    echo "📝 请编辑 .env 文件填入实际配置："
    echo "   vim .env"
    echo ""
    echo "必填项：DEEPSEEK_API_KEY、OSS_ACCESS_KEY、OSS_SECRET_KEY、OSS_BUCKET、OSS_URL"
    exit 1
fi

# 检查 Docker 是否安装
if ! command -v docker &> /dev/null; then
    echo "❌ 未安装 Docker，请先安装："
    echo "   curl -fsSL https://get.docker.com | sh"
    exit 1
fi

echo "🔨 构建镜像..."
# 不用 --no-cache：复用依赖下载层缓存，加快部署且减少网络波动导致的构建失败
docker compose build

# 若 mysql-data 卷为空（首次部署/换服务器），MySQL 会执行 initdb 建库。
# initdb 失败不会阻塞容器启动，也不阻塞后端启动，问题要等运行时才暴露，所以必须显式校验。
VOL_NAME="$(docker compose config --format json 2>/dev/null | grep -o '"name":"[^"]*mysql-data"' | head -1 | cut -d'"' -f4)"
[ -z "$VOL_NAME" ] && VOL_NAME="$(basename "$PWD")_mysql-data"
VOL_EXISTS="$(docker volume ls -q -f "name=^${VOL_NAME}$" 2>/dev/null || true)"

echo "🚀 启动服务..."
docker compose up -d

if [ -z "$VOL_EXISTS" ]; then
    echo ""
    echo "🔄 检测到首次初始化数据库，等待 MySQL 导入建库脚本..."
    for i in $(seq 1 60); do
        if docker exec furniture-mysql mysql -uroot -p"${MYSQL_ROOT_PASSWORD:-root}" \
             -e "SELECT 1 FROM \`furniture-system\`.\`order\` LIMIT 1;" >/dev/null 2>&1; then
            echo "✅ 数据库初始化完成（order 表已就绪）"
            break
        fi
        [ "$i" -eq 60 ] && {
            echo ""
            echo "❌ 数据库初始化失败或超时：order 表未建出！"
            echo "   请查看 MySQL 初始化日志定位报错："
            echo "     docker logs furniture-mysql"
            exit 1
        }
        sleep 2
    done
fi

echo ""
echo "=========================================="
echo "  ✅ 部署完成！"
echo "=========================================="
echo ""
echo "  前端访问: http://localhost"
echo "  后端 API: http://localhost:8080"
echo "  MySQL:    localhost:3307"
echo "  Redis:    localhost:6380"
echo ""
echo "  查看日志: docker compose logs -f"
echo "  停止服务: docker compose down"
echo "  重启服务: docker compose restart"
echo ""
