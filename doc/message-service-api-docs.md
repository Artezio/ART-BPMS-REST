# Message Service

* [Correlate](#correlate)

## Correlate
Correlates a message to the process engine to either trigger a message start event or an intermediate message catching event.

### `POST /api/message`

### Request body:
See [Message Object description].

### Result:
This method returns no content if the property resultEnabled is set to false, which is the default value. Otherwise, a JSON array of the message correlation results will be returned.
Each message correlation result has the following properties:

| Name | Value | Description |
| ---- | ----- | ----------- |
| resultType | String | Indicates if the message was correlated to a message start event or an intermediate message catching event. In the first case, the resultType is ProcessDefinition and otherwise Execution |
| processInstance | Object | This property only has a value if the resultType is set to ProcessDefinition. The processInstance with the properties as described in the [get single instance] method |
| execution | Object | This property only has a value if the resultType is set to Execution. The execution with the properties as described in the [get single execution] method |

### Response Codes:
| Code | Media Type | Description |
| ---- | ---------- | ----------- |
| 200 | application/json | Request successful. The property resultEnabled in the request body was true |
| 204 | | Request successful. The property resultEnabled in the request body was false (Default) |
| 400 | application/json | If no messageName was supplied. If both tenantId and withoutTenantId are supplied. If the message has not been correlated to exactly one entity (execution or process definition), or the variable value or type is invalid, for example if the value could not be parsed to an Integer value or the passed variable type is not supported. See the [Introduction] for the error response format |

### Example:

#### Request:
`POST /message`<p/>
Request Body:
```json
{
  "messageName" : "aMessage",
  "businessKey" : "aBusinessKey",
  "correlationKeys" : {
    "aVariable" : {"value" : "aValue", "type": "String"}
  },
  "processVariables" : {
    "aVariable" : {"value" : "aNewValue", "type": "String", 
                    "valueInfo" : { "transient" : true } },
    "anotherVariable" : {"value" : true, "type": "Boolean"}
  }
}
```

#### Response:
Status 204. No content.

[Message Object description]: https://docs.camunda.org/manual/7.10/reference/rest/message/post-message/#request-body