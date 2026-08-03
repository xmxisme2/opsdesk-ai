# OpsDesk AI Service

独立 AI/RAG 服务。当前已完成阶段 3：知识事件消费、Markdown 分块、腾讯云 Embedding、OpenSearch 向量索引与 BM25 + 向量混合检索。AI 总开关和 RAG 开关默认保持关闭。

## 本地初始化

```powershell
cd D:\OpsDesk\opsdesk-ai-service
.\sql\init-local-db.ps1
```

启动前为主应用和 AI 应用设置同一个 `AI_SERVICE_JWT_SECRET`，长度至少 32 个字符。Embedding 使用腾讯云 `lke-text-embedding-v2`；密钥通过环境变量或外部私密属性文件提供，不得提交到仓库。

```powershell
$env:AI_SERVICE_JWT_SECRET="<本地共享密钥>"
$env:TENCENT_SECRET_ID="<腾讯云 SecretId>"
$env:TENCENT_SECRET_KEY="<腾讯云 SecretKey>"
.\mvnw.cmd spring-boot:run
```

服务默认监听 `8081`。无需 Service JWT 的存活探针为 `GET /actuator/health/liveness`；主应用代理调用的受保护接口为 `POST /internal/health/check`。

## 检索评测

完成全量向量重建后，可复制 `evaluation/rag-recall-baseline.example.json` 建立业务评测集，并通过 `scripts/evaluate-retrieval.ps1` 计算 Recall@K。评测用短期 Service JWT 只允许通过 `AI_EVAL_SERVICE_TOKEN` 环境变量传入。当前仓库提供 12 篇专用文章和 36 条问题的合成基线，Recall@5 实测为 100%（36/36）；该结果只验证检索链路，不能替代真实业务样本。
