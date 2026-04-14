# BITShare API 接口文档

> 本文档基于 2026-04-14 实测结果整理

## 1. 概述

BITShare 是一个校内文件分享平台，提供公开资源访问和管理接口。

### 1.1 基础信息

| 项目 | 值 |
|------|-----|
| 推荐域名 | `https://app.bitshare.com.cn` |
| 内网 IP | `http://10.170.35.57:8890` |
| Content-Type | `application/json` |

### 1.2 接口测试状态总览

| 接口类型 | 状态 | 说明 |
|----------|------|------|
| 公开资源读取 | ✅ 正常 | 搜索、文件详情、下载可用 |
| 批量下载 | ✅ 正常 | 返回 ZIP 文件流 |
| 目录浏览 | ✅ 正常 | 目录树可正常获取 |
| 目录下载 | ❌ 失败 | 接口返回 404 |
| 文件列表(按目录) | ❌ 失败 | 接口返回 404 |
| 公开策略查询 | ❌ 失败 | 接口返回 404 |
| 管理接口 | ⚠️ 需凭据 | 登录接口存在，需正确账号密码 |

---

## 2. 公开资源接口 (已验证可用)

### 2.1 搜索文件或目录

**请求**
```http
GET /api/public/search?q={关键词}&page={页码}&page_size={每页数量}
```

**示例**
```bash
curl 'https://app.bitshare.com.cn/api/public/search?q=C语言&page=1&page_size=10'
```

**响应**
```json
{
  "items": [
    {
      "entity_type": "folder",
      "id": "e6ee97e5-58ae-4b86-8ade-93b962b49aeb",
      "name": "C语言"
    },
    {
      "entity_type": "file",
      "id": "856f3321-787d-4947-9030-86502b224a0c",
      "name": "test.c",
      "original_name": "test.c",
      "extension": ".c",
      "size": 139,
      "download_count": 16,
      "uploaded_at": "2026-03-19T08:38:05.373605061Z"
    }
  ],
  "page": 1,
  "page_size": 10,
  "total": 53
}
```

**说明**
- `entity_type` 值为 `folder` 或 `file`
- `page_size` 最大支持 100

---

### 2.2 获取文件详情

**请求**
```http
GET /api/public/files/{fileId}
```

**示例**
```bash
curl 'https://app.bitshare.com.cn/api/public/files/856f3321-787d-4947-9030-86502b224a0c'
```

**响应**
```json
{
  "id": "856f3321-787d-4947-9030-86502b224a0c",
  "name": "test.c",
  "extension": ".c",
  "folder_id": "014bc1c7-66c1-492f-ab09-a8e893903efe",
  "path": "BITshare / 课程资料 / C语言程序设计 / 其它资料 / 乐学代码",
  "description": "",
  "mime_type": "text/x-csrc; charset=utf-8",
  "size": 139,
  "uploaded_at": "2026-03-19T08:38:05.373605061Z",
  "download_count": 16
}
```

---

### 2.3 下载单个文件

**请求**
```http
GET /api/public/files/{fileId}/download
```

**示例**
```bash
curl -L 'https://app.bitshare.com.cn/api/public/files/856f3321-787d-4947-9030-86502b224a0c/download' -o test.c
```

**响应**
- 成功：返回文件二进制流
- `Content-Disposition` 头包含原始文件名

**实测结果**: ✅ 下载成功，文件内容正确

---

### 2.4 批量下载多个文件

**请求**
```http
POST /api/public/files/batch-download
Content-Type: application/json

{
  "file_ids": ["fileId1", "fileId2", ...]
}
```

**示例**
```bash
curl -X POST 'https://app.bitshare.com.cn/api/public/files/batch-download' \
  -H 'Content-Type: application/json' \
  -d '{"file_ids":["856f3321-787d-4947-9030-86502b224a0c","133e3096-8003-4c99-a33c-cdc8ee183082"]}' \
  -o batch.zip
```

**响应**
- 返回 ZIP 文件流

**实测结果**: ✅ 成功，ZIP 包含指定文件，保留原文件名

---

### 2.5 获取根目录列表

**请求**
```http
GET /api/public/folders
```

**示例**
```bash
curl 'https://app.bitshare.com.cn/api/public/folders'
```

**响应**
```json
{
  "items": [
    {
      "id": "ff2e6981-9737-4f54-94c5-8f8a67d71710",
      "name": "BITshare",
      "description": "请多多关注我们的导航页...",
      "updated_at": "2026-04-09T08:23:48.818698644Z",
      "file_count": 28576,
      "download_count": 48426,
      "total_size": 39010577468
    }
  ]
}
```

---

### 2.6 获取子目录列表

**请求**
```http
GET /api/public/folders?parent_id={folderId}
```

**示例**
```bash
curl 'https://app.bitshare.com.cn/api/public/folders?parent_id=ff2e6981-9737-4f54-94c5-8f8a67d71710'
```

**响应**
```json
{
  "items": [
    {
      "id": "48189a9b-2f02-4700-94e1-4d06c4b9dcdd",
      "name": "其他神秘资料",
      "description": "",
      "updated_at": "2026-04-09T16:20:07.0562391+08:00",
      "file_count": 12389,
      "download_count": 14402,
      "total_size": 15120055660
    }
  ]
}
```

**说明**: 不传 `parent_id` 时查询根目录

---

### 2.7 获取目录详情

**请求**
```http
GET /api/public/folders/{folderId}
```

**示例**
```bash
curl 'https://app.bitshare.com.cn/api/public/folders/1231c2cd-9760-414f-913c-bbd14e78f392'
```

**响应**
```json
{
  "id": "1231c2cd-9760-414f-913c-bbd14e78f392",
  "name": "C语言程序设计",
  "description": "",
  "parent_id": "8264ed5a-3725-474a-aebb-616ce1cff33d",
  "breadcrumbs": [
    { "id": "ff2e6981-9737-4f54-94c5-8f8a67d71710", "name": "BITshare" },
    { "id": "8264ed5a-3725-474a-aebb-616ce1cff33d", "name": "课程资料" },
    { "id": "1231c2cd-9760-414f-913c-bbd14e78f392", "name": "C语言程序设计" }
  ],
  "file_count": 366,
  "download_count": 6208,
  "total_size": 65219903,
  "updated_at": "2026-04-09T16:20:07.0562391+08:00"
}
```

---

## 3. 管理接口

### 3.1 管理员登录

**请求**
```http
POST /api/admin/session/login
Content-Type: application/json

{
  "username": "your_admin",
  "password": "your_password"
}
```

**示例**
```bash
curl -X POST 'https://app.bitshare.com.cn/api/admin/session/login' \
  -H 'Content-Type: application/json' \
  -d '{"username":"BITshare","password":"BITshare"}'
```

**响应 (成功)**
```json
{
  "admin": {
    "id": "admin_xxx",
    "username": "your_admin",
    "display_name": "系统管理员",
    "avatar_url": "",
    "role": "admin",
    "status": "active",
    "permissions": ["resource_moderation"]
  }
}
```

**响应 (失败)**
```json
{ "error": "invalid username or password" }
```

**说明**: 接口存在，但需要正确的管理员账号密码

---

## 4. 无法访问的接口 (404)

以下接口在实测中返回 404，可能是文档描述与实际实现不一致：

| 接口 | 文档路径 | 说明 |
|------|----------|------|
| 按目录获取文件列表 | `GET /api/public/files?folder_id=` | 返回 404 |
| 目录下载 | `GET /api/public/folders/{folderId}/download` | 返回 404 |
| 公开策略查询 | `GET /api/public/system/policy` | 返回 404 |
| 公开文件列表 | `GET /api/public/files` | 返回 404 |

---

## 5. Android 端集成

### 5.1 Retrofit 接口定义

```kotlin
interface BitShareApiService {
    @GET("api/public/search")
    suspend fun searchFiles(
        @Query("q") query: String,
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 20
    ): BitShareSearchResponse

    @GET("api/public/files/{fileId}")
    suspend fun getFileDetail(
        @Path("fileId") fileId: String
    ): BitShareFileDetailDto

    @Streaming
    @GET("api/public/files/{fileId}/download")
    suspend fun downloadFile(
        @Path("fileId") fileId: String
    ): ResponseBody
}
```

### 5.2 网络配置

```kotlin
// NetworkModule.kt
private const val BIT_SHARE_BASE_URL = "https://app.bitshare.com.cn/"

@Provides
@Singleton
fun provideBitShareApiService(@BasicRetrofit okHttpClient: OkHttpClient): BitShareApiService {
    return Retrofit.Builder()
        .baseUrl(BIT_SHARE_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(BitShareApiService::class.java)
}
```

---

## 6. 建议

### 6.1 当前问题

1. **文件列表接口缺失**: 文档中的 `/api/public/files?folder_id=` 返回 404，无法按目录获取文件列表
2. **目录下载接口缺失**: 无法直接下载整个目录
3. **管理接口需要账号**: 无法获取测试用账号进行完整测试

### 6.2 替代方案

由于 `/api/public/files?folder_id=` 接口不可用，可以采用：

1. **使用搜索接口**: 通过搜索关键词过滤文件
2. **使用目录树浏览**: 通过 `/api/public/folders?parent_id=` 逐级浏览
3. **批量下载**: 收集文件 ID 后调用 batch-download 接口

---

## 7. 参考

- 基础地址: `https://app.bitshare.com.cn`
- 测试日期: 2026-04-14
- 测试工具: curl
