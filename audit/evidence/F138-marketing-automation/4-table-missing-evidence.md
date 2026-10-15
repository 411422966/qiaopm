{
  "test_summary": "F138 N4 I063 实证：4 张 mkt 表 MISSING 触达 TableNotFoundException",
  "tables_checked": ["mkt_invitation", "mkt_invitation_rule", "mkt_dispatch", "mkt_dispatch_target"],
  "all_missing": true,
  "endpoints_tested": [
    {
      "endpoint": "GET /m12/dispatch/page?pageNum=1&pageSize=20",
      "http_code": 200,
      "business_code": 500,
      "response": {
        "code": 500,
        "message": "数据库异常",
        "data": null,
        "traceId": "e5908f4546a94449bba95d27db4e443d",
        "timestamp": "2026-09-16 21:55:14"
      }
    },
    {
      "endpoint": "GET /m12/dispatch/stats",
      "http_code": 200,
      "business_code": 500,
      "response": {
        "code": 500,
        "message": "数据库异常",
        "data": null,
        "traceId": "671da1d5575a423d903ef4bc5b085a46",
        "timestamp": "2026-09-16 21:55:23"
      }
    },
    {
      "endpoint": "GET /m12/dispatch/1",
      "http_code": 200,
      "business_code": 500,
      "response": {
        "code": 500,
        "message": "数据库异常",
        "data": null,
        "traceId": "ac6d9a727c0a43739743cc0da22f804d",
        "timestamp": "2026-09-16 21:55:23"
      }
    }
  ],
  "expected": "BusinessPolicyDispatchController 任一端点调 MktDispatchMapper / MktDispatchTargetMapper → TableNotFoundException → 500 数据库异常",
  "actual": "符合预期：3 个端点全部返回 code=500 message='数据库异常'",
  "root_cause": "MktDispatchMapper / MktDispatchTargetMapper / MktInvitationMapper / MktInvitationRuleMapper 实体类已建，但 4 张表从未 CREATE",
  "log_evidence": "meiyetec-error.log: 'java.sql.SQLSyntaxErrorException: Table 'meiyetec.mkt_dispatch' doesn't exist'",
  "issue": "I063 CRITICAL-P2"
}