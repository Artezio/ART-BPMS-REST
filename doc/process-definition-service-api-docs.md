# Process Definition Service

* [Start](#start)
* [Load Start Form](#load-start-form)
* [Get List](#get-list)

## Start
Instantiate a process definition. If process definition has a start form, all variables passed to the method will pass "clean up and validation" procedure.
Due to the procedure some variables might not get into the process. If some variable has two or more files with identical names,
the only one of these files will be used and it is not determined which one will be chosen.

### `POST /api/process-definition/key/{process-definition-key}/start`

### Parameters:
| Name                   | Type       | Description                                    | Required |
| ---------------------- | ---------- | ---------------------------------------------- | -------- |
| process-definition-key | path       | The key of the process definition to be started  | Yes      |

### Request Body:
A JSON object with variables. May be empty.

### Result:
A JSON object representing the next task assigned to the user who made this request, if any. For information about properties
see [task info].

### Response Codes:

| Code | Description |
| ---- | ----------- |
| 200  | Request successful |
| 204  | Request successful, but there are no tasks assigned to the user |
| 403  | The user is not allowed to start the process |

### Example:

#### Request:
`POST /api/process-definition/key/some-process-definition-key/start`

#### Response:
Status 200 OK.
```json
{
  "id":"anId",
  "name":"aName",
  "assignee":"anAssignee",
  "created":"2013-01-23T13:42:42.000+0200",
  "due":"2013-01-23T13:49:42.576+0200",
  "followUp":"2013-01-23T13:44:42.437+0200",
  "delegationState":"RESOLVED",
  "description":"aDescription",
  "executionId":"anExecution",
  "owner":"anOwner",
  "parentTaskId":"aParentId",
  "priority":42,
  "processDefinitionId":"aProcDefId",
  "processInstanceId":"aProcInstId",
  "caseDefinitionId":"aCaseDefId",
  "caseInstanceId":"aCaseInstId",
  "caseExecutionId":"aCaseExecution",
  "taskDefinitionKey":"aTaskDefinitionKey",
  "suspended": false,
  "formKey":"aFormKey",
  "tenantId":"aTenantId"
}
```

## Load Start Form
Load the start form definition for the process, if any.

### `GET /api/process-definition/key/{process-definition-key}/form`

### Parameters:
| Name | Type | Description | Required |
| ---- | ---------- | ----------- | -------- |
| process-definition-key | path | The key of the process definition, which form is loaded for | Yes |

### Result:
A JSON object representing requested form definition. For detailed information about form definition fields see corresponding
documentation.

### Response Codes:

| Code | Media Type | Description |
| ---- | ---------- | ----------- |
| 200 | application/json | Request successful |
| 403 | | The user doesn't have an access to load start form for the process |
| 404 | | No deployed form for a given process definition exists |

### Example:

#### Request:
Request to Formio server: `GET /api/process-definition/key/some-process-definition-key/form`

#### Response:
Status 200 OK.
```json
{
    "_id": "anId",
    "type": "aType",
    "tags": [
        ...
    ],
    "owner": "anOwner",
    "components": [ ... ],
    "access": [
        {
            "roles": [ ... ],
            "type": "aType"
        }
    ],
    "created": "2019-06-28T15:00:41.690Z",
    "display": "aDisplay",
    "title": "aTitle",
    "machineName": "aMachineName",
    "path": "aPath",
    "submissionAccess": [...],
    "name": "aName",
    "modified": "2019-10-07T13:53:29.519Z"
}
```

## Get List
List process definitions startable by the user created this request.

### `GET /api/process-definition`

### Result:
A list of JSON objects representing process definitions. For information about process definition fields see 
[process definition info]. 

### Response Codes:
| Code | Description |
| ---- | ----------- |
| 200 | Successful request |
| 204 | There are no processes startable by the user |

### Example:

#### Request:
`GET /api/process-definition`

#### Response:
```json
 [
    {
      "id": "invoice:1:c3a63aaa-2046-11e7-8f94-34f39ab71d4e",
      "key": "invoice",
      "category": "http://www.omg.org/spec/BPMN/20100524/MODEL",
      "description": null,
      "name": "Invoice Receipt",
      "version": 1,
      "resource": "invoice.v1.bpmn",
      "deploymentId": "c398cd26-2046-11e7-8f94-34f39ab71d4e",
      "diagram": null,
      "suspended": false,
      "tenantId": null,
      "versionTag": null,
      "historyTimeToLive": 5,
      "startableInTasklist": true
    },
    {
      "id": "invoice:2:c3e1bd16-2046-11e7-8f94-34f39ab71d4e",
      "key": "invoice",
      "category": "http://www.omg.org/spec/BPMN/20100524/MODEL",
      "description": null,
      "name": "Invoice Receipt",
      "version": 2,
      "resource": "invoice.v2.bpmn",
      "deploymentId": "c3d82020-2046-11e7-8f94-34f39ab71d4e",
      "diagram": null,
      "suspended": false,
      "tenantId": null,
      "versionTag": null,
      "historyTimeToLive": null,
      "startableInTasklist": true
    }
  ]
```

[task info]: https://docs.camunda.org/manual/7.10/reference/rest/task/get/#result
[process definition info]: https://docs.camunda.org/manual/7.10/reference/rest/process-definition/get-query/#result