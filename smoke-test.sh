#!/bin/bash
# nimbus-cloud 端到端冒烟测试(依赖: curl, sed)
BASE=http://127.0.0.1:8080
PASS=0; FAIL=0
ok()   { PASS=$((PASS+1)); echo "✅ $1"; }
bad()  { FAIL=$((FAIL+1)); echo "❌ $1 -> $2"; }
check() { if echo "$2" | grep -q "$3"; then ok "$1"; else bad "$1" "$2"; fi }
# 提取响应中的指定字段, 未命中输出空串
F() { echo "$1" | sed -nE "s/.*\"$2\":\"?([^\",}]+)\"?.*/\1/p" | head -1; }

# 1. 登录管理账号
LOGIN=$(curl -s -X POST $BASE/api/auth/login -H 'Content-Type: application/json' -d '{"username":"admin","password":"admin123"}')
TOKEN=$(F "$LOGIN" token)
if [ -n "$TOKEN" ] && [ "$TOKEN" != "$LOGIN" ]; then ok "admin 登录"; else bad "admin 登录" "$LOGIN"; fi
AUTH="Authorization: $TOKEN"

# 2. 注册新用户
REG=$(curl -s -X POST $BASE/api/auth/register -H 'Content-Type: application/json' -d '{"username":"smoke","password":"smoke123","nickname":"smoke-user"}')
check "用户注册" "$REG" '"code":200'

# 3. 新用户登录
LOGIN2=$(curl -s -X POST $BASE/api/auth/login -H 'Content-Type: application/json' -d '{"username":"smoke","password":"smoke123"}')
TOKEN2=$(F "$LOGIN2" token)
check "smoke 登录" "$LOGIN2" '"code":200'
AUTH2="Authorization: $TOKEN2"

# 4. 创建文件夹
FOLDER=$(curl -s -X POST $BASE/api/netdisk/folder -H "$AUTH" -H 'Content-Type: application/json' -d '{"parentId":0,"folderName":"work-docs"}')
FOLDER_ID=$(F "$FOLDER" data)
check "创建文件夹" "$FOLDER" '"code":200'

# 5. 文件夹目录树
TREE=$(curl -s $BASE/api/netdisk/folder/tree -H "$AUTH")
check "目录树" "$TREE" 'work-docs'

# 6. 上传小文件(整体上传)
echo "hello nimbus cloud" > /tmp/demo.txt
UP=$(curl -s -X POST $BASE/api/upload/single -H "$AUTH" -F "file=@/tmp/demo.txt" -F "folderId=$FOLDER_ID")
FILE_ID=$(F "$UP" id)
check "文件上传" "$UP" 'demo.txt'

# 7. 文件列表
PAGE=$(curl -s "$BASE/api/netdisk/file/page?pageNum=1&pageSize=10&folderId=$FOLDER_ID" -H "$AUTH")
check "文件列表" "$PAGE" 'demo.txt'

# 8. 搜索
SEARCH=$(curl -s "$BASE/api/search/file?keyword=demo" -H "$AUTH")
check "文件搜索" "$SEARCH" 'demo.txt'

# 9. 收藏
STAR=$(curl -s -X PUT "$BASE/api/netdisk/file/$FILE_ID/star?starred=true" -H "$AUTH")
STARRED=$(curl -s $BASE/api/netdisk/file/starred -H "$AUTH")
check "收藏文件" "$STARRED" 'demo.txt'

# 10. 配额
QUOTA=$(curl -s $BASE/api/quota -H "$AUTH")
check "配额查询" "$QUOTA" '"totalSize"'

# 11. 预览 + Range 内容
PREVIEW=$(curl -s "$BASE/api/netdisk/preview/$FILE_ID" -H "$AUTH")
check "预览信息" "$PREVIEW" '"code":200'
RANGE=$(curl -s -r 0-4 "$BASE/api/netdisk/preview/$FILE_ID/content" -H "$AUTH")
check "Range 预览内容" "$RANGE" 'hello'

# 12. 创建分享(权限: 可转存)
SHARE=$(curl -s -X POST $BASE/api/share -H "$AUTH" -H 'Content-Type: application/json' -d "{\"targetType\":1,\"targetIds\":[$FILE_ID],\"shareType\":1,\"permission\":3,\"expireType\":1}")
CODE=$(F "$SHARE" shortCode)
check "创建分享" "$SHARE" 'shortCode'

# 13. 免登录访问分享
ACCESS=$(curl -s -X POST $BASE/api/share/access -H 'Content-Type: application/json' -d "{\"code\":\"$CODE\"}")
check "访问分享" "$ACCESS" 'demo.txt'

# 14. 免登录下载分享
DL=$(curl -s "$BASE/api/share/$CODE/download/$FILE_ID")
check "分享下载" "$DL" 'hello nimbus cloud'

# 15. 转存到 smoke 用户
SAVE=$(curl -s -X POST $BASE/api/share/save -H "$AUTH2" -H 'Content-Type: application/json' -d "{\"code\":\"$CODE\",\"folderId\":0}")
check "分享转存" "$SAVE" '"code":200'
SAVED=$(curl -s "$BASE/api/netdisk/file/page?keyword=demo" -H "$AUTH2")
check "转存后文件存在" "$SAVED" 'demo.txt'

# 16. 秒传检测(相同内容重复上传)
HASH=$(sha256sum /tmp/demo.txt | cut -d' ' -f1)
SIZE=$(stat -c%s /tmp/demo.txt)
INSTANT=$(curl -s -X POST $BASE/api/upload/init -H "$AUTH" -H 'Content-Type: application/json' -d "{\"fileName\":\"demo-copy.txt\",\"fileSize\":$SIZE,\"fileHash\":\"$HASH\",\"folderId\":0}")
check "秒传检测" "$INSTANT" '"instant":true'

# 17. 上传新版本(init->chunk->merge)
echo "hello nimbus cloud v2" > /tmp/demo2.txt
HASH2=$(sha256sum /tmp/demo2.txt | cut -d' ' -f1)
SIZE2=$(stat -c%s /tmp/demo2.txt)
INIT2=$(curl -s -X POST $BASE/api/upload/init -H "$AUTH" -H 'Content-Type: application/json' -d "{\"fileName\":\"demo.txt\",\"fileSize\":$SIZE2,\"fileHash\":\"$HASH2\",\"folderId\":$FOLDER_ID,\"fileId\":$FILE_ID}")
UPID=$(F "$INIT2" uploadId)
CHUNK=$(curl -s -X POST $BASE/api/upload/chunk -H "$AUTH" -F "uploadId=$UPID" -F "chunkIndex=0" -F "file=@/tmp/demo2.txt")
MERGE=$(curl -s -X POST $BASE/api/upload/merge -H "$AUTH" -H 'Content-Type: application/json' -d "{\"uploadId\":\"$UPID\"}")
check "新版本上传" "$MERGE" '"version":2'
VERS=$(curl -s "$BASE/api/netdisk/file/$FILE_ID/versions" -H "$AUTH")
check "版本列表" "$VERS" '"versionNo":1'

# 18. 回滚到 v1
VER_ID=$(F "$VERS" id)
ROLL=$(curl -s -X PUT "$BASE/api/netdisk/file/$FILE_ID/rollback/$VER_ID" -H "$AUTH")
check "版本回滚" "$ROLL" '"version":3'

# 19. 回收站: 删除 -> 列表 -> 恢复
DEL=$(curl -s -X DELETE "$BASE/api/netdisk/file/$FILE_ID" -H "$AUTH")
check "删除到回收站" "$DEL" '"code":200'
RECYCLE=$(curl -s "$BASE/api/recycle/page?pageNum=1&pageSize=10" -H "$AUTH")
check "回收站列表" "$RECYCLE" 'demo.txt'
RESTORE=$(curl -s -X PUT "$BASE/api/recycle/restore?targetType=1&id=$FILE_ID" -H "$AUTH")
check "回收站恢复" "$RESTORE" '"code":200'

# 20. 删除文件夹到回收站 -> 彻底删除
FOLDER_DEL=$(curl -s -X DELETE "$BASE/api/netdisk/folder/$FOLDER_ID" -H "$AUTH")
check "文件夹回收" "$FOLDER_DEL" '"code":200'
PURGE=$(curl -s -X DELETE "$BASE/api/recycle/1/$FILE_ID" -H "$AUTH")
PURGE2=$(curl -s -X DELETE "$BASE/api/recycle/2/$FOLDER_ID" -H "$AUTH")
check "彻底删除" "$PURGE2" '"code":200'

# 21. 账号密码错误
BADLOGIN=$(curl -s -X POST $BASE/api/auth/login -H 'Content-Type: application/json' -d '{"username":"admin","password":"wrong"}')
check "错误密码拦截" "$BADLOGIN" '账号或密码错误'

# 22. 未登录访问拦截
NOAUTH=$(curl -s $BASE/api/quota)
check "未登录拦截" "$NOAUTH" '"code":401'

# 23. 审计日志(管理员)
OPLOG=$(curl -s "$BASE/api/system/operlog/page?pageNum=1&pageSize=5" -H "$AUTH")
check "审计日志" "$OPLOG" '"code":200'

# 24. 下载回归: 重新上传并流式下载
echo "download check" > /tmp/dl.txt
UP3=$(curl -s -X POST $BASE/api/upload/single -H "$AUTH" -F "file=@/tmp/dl.txt" -F "folderId=0")
FID3=$(F "$UP3" id)
DL3=$(curl -s "$BASE/api/netdisk/download/file/$FID3" -H "$AUTH")
check "流式下载" "$DL3" 'download check'

echo ""
echo "========== 冒烟结果: PASS=$PASS FAIL=$FAIL =========="