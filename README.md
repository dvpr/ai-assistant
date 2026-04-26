# AI Assistant - 智能本地助手

## 项目概述

一个运行在Android设备上的智能AI助手，支持**本地模型推理**和**云端API调用**两种模式。核心特点是**可扩展的记忆系统**和**多级安全验证**，让AI真正成为属于你个人的专属助手。

---

## 项目背景与动机

### 为什么做这个项目？

1. **隐私担忧**：云端AI服务的对话数据可能被收集分析
2. **网络依赖**：离线场景下无法使用智能助手
3. **缺乏个性化**：通用AI不记得用户的历史信息
4. **安全顾虑**：敏感信息（如密码）不想让云端知道

### 设计目标

- ✅ 本地优先，云端为辅
- ✅ 可记忆用户信息
- ✅ 多级安全保护
- ✅ 灵活切换模型
- ✅ 完整的对话日志

---

## 技术架构

### 整体架构图

```
┌─────────────────────────────────────────────────────────────┐
│                      Android App                            │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐         │
│  │    UI层     │  │  对话管理层  │  │  记忆管理层  │         │
│  └─────────────┘  └─────────────┘  └─────────────┘         │
│         ↓               ↓               ↓                   │
│  ┌─────────────────────────────────────────────────────┐   │
│  │                   核心服务层                          │   │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────────────┐    │   │
│  │  │本地推理引擎│ │云端API调用│ │安全验证模块       │    │   │
│  │  └──────────┘ └──────────┘ └──────────────────┘    │   │
│  └─────────────────────────────────────────────────────┘   │
│         ↓               ↓               ↓                   │
│  ┌─────────────────────────────────────────────────────┐   │
│  │                   数据存储层                         │   │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────────────┐    │   │
│  │  │ SQLite   │ │加密存储   │ │Android Keystore  │    │   │
│  │  └──────────┘ └──────────┘ └──────────────────┘    │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│                    外部服务                                  │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐                    │
│  │llama.cpp │ │智谱AI API│ │硅基流动API│                    │
│  └──────────┘ └──────────┘ └──────────┘                    │
└─────────────────────────────────────────────────────────────┘
```

### 核心组件

| 组件 | 技术选型 | 说明 |
|------|----------|------|
| **本地推理** | llama.cpp + JNI | 支持GGUF格式模型 |
| **云端调用** | OkHttp + SSE | 流式输出，支持OpenAI兼容接口 |
| **数据库** | SQLite | 存储记忆和对话日志 |
| **加密** | Android Keystore | 敏感信息加密存储 |
| **安全验证** | Fingerprint/PIN/Pattern | 多级可配置安全策略 |

---

## 核心功能实现

### 1. 模型管理系统

#### 功能特性
- 支持从URL下载GGUF模型
- 多模型本地存储与管理
- 一键切换使用模型
- 国内镜像加速下载

#### 实现机制

```java
// 模型信息存储
private static final String MODEL_URL = "https://hf-mirror.com/...";
private static final String MODEL_FILE_NAME = "model.gguf";

// 下载到私有目录（不需要存储权限）
File modelFile = new File(context.getFilesDir(), fileName);

// 模型切换时清理旧资源
cleanup();
loadModel(newModelPath);
```

---

### 2. 云端API集成

#### 支持平台

| 平台 | API地址 | 免费模型 |
|------|---------|----------|
| **智谱AI** | `https://open.bigmodel.cn/api/paas/v4/chat/completions` | `glm-4-flash` |
| **硅基流动** | `https://api.siliconflow.cn/v1/chat/completions` | `Qwen/Qwen3-8B-Free` |

#### 流式响应实现

```java
// SSE流式读取
while ((line = reader.readLine()) != null) {
    if (line.startsWith("data: ")) {
        String data = line.substring(6);
        if ("[DONE]".equals(data)) break;
        
        JSONObject json = new JSONObject(data);
        String content = json.getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("delta")
            .getString("content");
        
        // 逐字显示
        callback.onToken(content);
    }
}
```

---

### 3. 记忆系统

#### 数据库设计

```sql
-- 分类表（可扩展）
CREATE TABLE categories (
    id INTEGER PRIMARY KEY,
    name TEXT UNIQUE,           -- Security, Personal, Work...
    display_name TEXT,          -- 显示名称
    policy_type TEXT,           -- 安全策略类型
    is_setup INTEGER DEFAULT 0
);

-- 记忆数据表
CREATE TABLE memories (
    id INTEGER PRIMARY KEY,
    category_id INTEGER,        -- 关联分类
    key_text TEXT,              -- 规范化关键词
    value_text TEXT,            -- 加密存储的值
    raw_input TEXT,             -- 原始输入
    created_at INTEGER
);

-- 对话日志表（安全等级最高）
CREATE TABLE conversation_logs (
    id INTEGER PRIMARY KEY,
    session_id TEXT,
    user_input TEXT,
    ai_response TEXT,
    intent_type TEXT,           -- STORE/QUERY/NORMAL
    created_at INTEGER
);
```

#### 记忆存储流程

```
用户: "帮我记一下1.1.1.1的密码是abc123"
    ↓
AI意图识别 → 提取key-value → 推荐分类
    ↓
用户确认分类（Security）
    ↓
自动使用分类默认安全策略
    ↓
加密存储到数据库
    ↓
响应: "已记住1.1.1.1的密码"
```

---

### 4. 多级安全验证

#### 安全策略体系

| 安全等级 | 适用对象 | 默认策略 | 说明 |
|----------|----------|----------|------|
| 🔒🔒🔒 最高 | 对话日志 | 指纹+PIN+设备锁 | 包含所有敏感信息 |
| 🔒🔒 高 | 安全信息/财务信息 | 指纹验证 | 密码、密钥等 |
| 🔒 中 | 个人信息/健康信息 | 设备锁 | 生日、病历等 |
| 无 | 日常信息 | 无验证 | 不敏感信息 |

#### 策略组合支持

```java
// 支持 AND/OR 组合
"fingerprint AND pin"     // 指纹和PIN都必须通过
"fingerprint OR pin"      // 指纹或PIN任一通过
"fingerprint AND pin AND device_lock"  // 三重验证
```

#### 验证缓存机制

```java
// 验证成功后缓存，5分钟内无需重复验证
if (lastVerifyResult && (now - lastVerifyTime) < VERIFY_TIMEOUT) {
    return true;  // 直接通过
}
```

---

### 5. 对话日志系统

#### 特殊设计

- **独立安全策略**：对话日志有自己独立的安全配置
- **默认最高等级**：预设为指纹+PIN+设备锁三重验证
- **可自定义**：用户可在设置中修改验证方式
- **不可绕过**：任何访问都要经过验证
- **加密存储**：日志内容加密，root设备也无法直接读取

#### 使用场景

| 场景 | 说明 |
|------|------|
| **审计追溯** | 查看谁在什么时候问了什么问题 |
| **记忆冲突解决** | 当记忆被覆盖时，可回溯历史 |
| **安全监控** | 检测是否有异常查询模式 |
| **导出备份** | 导出加密的对话记录 |

---

## 项目实现历程

### 第一阶段：基础框架搭建

1. **llama.cpp集成**
   - 使用CMake + FetchContent拉取llama.cpp
   - JNI封装loadModel/generateText方法
   - 解决tokenize兼容性问题（7参数签名）

2. **模型下载与管理**
   - 实现从URL下载GGUF模型
   - 保存到应用私有目录（无需存储权限）
   - 多模型切换与持久化

### 第二阶段：本地推理调优

1. **模型测试**
   - TinyLlama-1.1B：速度快但中文弱，只能做续写
   - Qwen2-0.5B：速度快，翻译好，简单问答
   - Qwen2-1.5B：质量好但速度慢（2分钟/32tokens）

2. **Tokenize问题解决**
   - 发现`llama_tokenize`签名是7参数
   - 实现扩容重试机制避免-2错误

### 第三阶段：云端集成

1. **智谱AI接入**
   - API地址：`https://open.bigmodel.cn/api/paas/v4/chat/completions`
   - 模型：glm-4-flash（免费，永久有效）

2. **流式输出实现**
   - SSE流式读取逐字显示
   - 实时显示生成进度

### 第四阶段：记忆与安全系统

1. **数据库设计**
   - SQLite存储分类、记忆、对话日志
   - 支持外键关联和数据加密

2. **安全验证框架**
   - 分类级别安全策略
   - 支持指纹/PIN/图案/设备锁及组合
   - 验证结果超时缓存

---

## 模型评测结果

### 本地模型对比

| 模型 | 参数量 | 速度(32 tokens) | 中文能力 | 推荐场景 |
|------|--------|-----------------|----------|----------|
| TinyLlama-1.1B | 1.1B | ~10分钟 | ⭐⭐ | 英文续写 |
| Qwen2-0.5B | 0.5B | ~17秒 | ⭐⭐⭐ | 翻译、简单问答 |
| Qwen2-1.5B | 1.5B | ~2分钟 | ⭐⭐⭐⭐ | 创意生成、对话 |

### 云端模型对比

| 平台 | 模型 | 速度 | 免费额度 | 推荐度 |
|------|------|------|----------|--------|
| 智谱AI | glm-4-flash | 2-5秒 | 永久免费 | ⭐⭐⭐⭐⭐ |
| 硅基流动 | Qwen3-8B-Free | 3-8秒 | 赠额度 | ⭐⭐⭐⭐ |

### 结论

- **日常使用推荐**：云端智谱AI（快速、免费、效果好）
- **离线场景**：Qwen2-0.5B（快速，能处理简单任务）
- **创意场景**：Qwen2-1.5B（质量好，适合写诗续写）

---

## 使用指南

### 快速开始

1. **下载App**：编译安装到Android手机
2. **选择模式**：
   - 云端模式：输入API Key即可使用
   - 本地模式：下载GGUF模型后使用

3. **记忆功能**：
   ```
   "帮我记一下服务器192.168.1.1的密码是admin123"
   "192.168.1.1的密码是什么？"
   ```

4. **安全设置**：
   - 在设置中为不同分类配置验证方式
   - 对话日志默认需要三重验证

### 常用命令

| 命令示例 | 功能 |
|----------|------|
| "记一下xxx" | 存储记忆 |
| "xxx是什么" | 查询记忆 |
| "讲个笑话" | 普通对话 |
| "查看历史" | 查看对话日志（需验证） |

---

## 目录结构

```
app/src/main/
├── java/com/example/llama/
│   ├── MainActivity.java           # 主界面
│   ├── DebugLogger.java            # 屏幕日志
│   ├── TextGenerator.java          # 生成接口
│   ├── LocalGenerator.java         # 本地推理
│   ├── CloudGenerator.java         # 云端调用
│   ├── ModelManager.java           # 模型管理
│   ├── ModelSelectionFragment.java # 模型选择UI
│   ├── DownloadTask.java           # 模型下载
│   └── memory/                     # 记忆系统
│       ├── MemoryContract.java     # 数据库契约
│       ├── MemoryDBHelper.java     # 数据库操作
│       ├── MemoryManager.java      # 记忆管理
│       ├── ConversationLogger.java # 对话日志
│       ├── SecurityVerifier.java   # 安全验证
│       └── models/*.java           # 数据模型
├── cpp/
│   ├── wrapper.cpp                 # JNI封装
│   └── CMakeLists.txt              # CMake配置
└── res/                            # 资源文件
```

---

## 技术难点与解决方案

### 1. llama.cpp Tokenize兼容性问题

**问题**：`llama_tokenize`返回-2错误
**原因**：函数签名与调用方式不匹配
**解决**：通过`grep`查看头文件确认7参数签名，实现扩容重试机制

### 2. 模型下载权限问题

**问题**：无法写入Download目录
**解决**：改用`context.getFilesDir()`保存到私有目录，无需存储权限

### 3. 生成速度慢

**问题**：1.5B模型生成32 tokens需要2分钟
**解决**：提供模型选择建议，推荐云端方案，或使用0.5B模型

### 4. 模型格式遵循差

**问题**：Base模型只会续写，不会回答问题
**解决**：明确区分Base和Instruct模型，使用正确的对话模板

---

## 后续优化方向

- [ ] 实现真正的指纹/PIN验证UI
- [ ] AI自动分类记忆内容
- [ ] 对话历史搜索功能
- [ ] 语音输入支持
- [ ] 数据云端备份（加密）
- [ ] 更多本地模型测试（Phi-2、GLM-Edge）
- [ ] 模型量化工具集成
- [ ] 多语言支持

---

## 开源协议

MIT License

---

## 联系方式

- GitHub：[dvpr/ai-assistant](https://github.com/dvpr/ai-assistant)

---

*最后更新：2026年4月*