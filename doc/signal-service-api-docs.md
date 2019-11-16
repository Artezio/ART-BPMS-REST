# Signal Service

* [Throw a Signal](#throw-a-signal)

## Throw a Signal
A signal is an event of global scope (broadcast semantics) and is delivered to all active handlers.

### `POST /api/signal`

### Request body:
See [Signal Object description].

### Result:
This method returns no content.

### Response Codes:
| Code | Media Type | Description |
| ---- | ---------- | ----------- |
| 204  | | Request successful |
| 400  | application/json | * If no name was given |
|      |                  | * If the variable value or type is invalid, for example if the value could not be parsed to an integer value or the passed variable type is not supported |
|      |                  | * If a tenant id and an execution id is specified
| 403  | application/json | if the user is not allowed to throw a signal event |
| 500  | application/json | If a single execution is specified and no such execution exists or has not subscribed to the signal |

### Example:

#### Request:
`POST /signal`<p>
Request Body:
```json
{
  "name": "policy_conditions_changed",
  "variables": {
    "newTimePeriodInMonth": {
      "value": 24
    }
  }
}
```

#### Response:
Status 204. No content.

[Signal Object description]: https://docs.camunda.org/manual/7.10/reference/rest/signal/post-signal/#request-body