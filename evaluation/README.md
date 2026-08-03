# RAG 检索评测集

`rag-recall-baseline.json` 是本地 Recall@5 基线，共 12 篇专用知识文章、36 个问题，每篇文章覆盖精确错误码、自然语言和对比干扰三类问法。

## 准备数据

使用 MySQL 客户端的 `source` 方式执行，脚本可重复运行：

```powershell
mysql --default-character-set=utf8mb4 -h localhost -uroot -p -e "source D:/OpsDesk/opsdesk-backend/sql/11_seed_rag_recall_evaluation.sql"
```

脚本只维护固定 ID 区间：

- 分类：`880030010000000001`～`880030010000000006`
- 标签：`880030020000000001`～`880030020000000012`
- 文章：`880030030000000001`～`880030030000000012`
- 文章标签关系：`880030040000000001`～`880030040000000024`

写入后需要执行一次向量全量重建，再设置短期 Service JWT：

```powershell
$env:AI_EVAL_SERVICE_TOKEN="<短期 Service JWT>"
.\scripts\evaluate-retrieval.ps1 -DatasetPath .\evaluation\rag-recall-baseline.json -K 5
```

评测脚本只读取知识搜索结果，不修改文章或索引。

本地环境也可使用一键脚本。它会在内存中生成临时共享密钥，启动主应用与 AI 服务，执行全量重建和评测，最后关闭本轮启动的进程：

```powershell
.\scripts\run-local-recall-evaluation.ps1 -K 5
```
