<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib uri="http://java.sun.com/jstl/core" prefix="c" %>

<!doctype html>
<html lang="ru">

<%
    final String KEYCLOAK_SERVER_URL = System.getProperty("KEYCLOAK_SERVER_URL", "http://localhost:8180/auth");
    final String BPMS_REST_API = System.getProperty("BPMS_REST_API", "http://localhost:8080/bpms-rest/api");
    final String KEYCLOAK_REALM = System.getProperty("KEYCLOAK_REALM", "master");
    final String KEYCLOAK_CLIENT_ID = System.getProperty("KEYCLOAK_CLIENT_ID", "bpms-rest");
    final String FILE_STORAGE_URL = System.getenv("FILE_STORAGE_URL");
%>

<head>
    <title>Dashboard</title>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
    <link rel='stylesheet' href='css/bootstrap-3.3.7.min.css'>
    <link rel='stylesheet' href='css/formio.full-3.22.9.min.css'>
    <script src="<%=KEYCLOAK_SERVER_URL%>/js/keycloak.js" type="application/javascript"></script>
    <script src="js/lib/jquery-3.3.1.min.js" type="application/javascript"></script>
    <script src='js/lib/formio.full-3.22.9.min.js' type="application/javascript"></script>
    <script src='js/bpms-rest-spa.js' type="application/javascript"></script>
    <style type="text/css">
        .table-borderless table,
        .table-borderless tbody > tr > td,
        .table-borderless tbody > tr > th,
        .table-borderless tfoot > tr > td,
        .table-borderless tfoot > tr > th,
        .table-borderless thead > tr > td,
        .table-borderless thead > tr > th {
            border: none;
        }
    </style>
</head>

<body>
<nav class="navbar navbar-default" style="background-color: #d9edf7">
    <div class="container-fluid">
        <div class="navbar-header">
            <span class="navbar-brand">
                <strong>BPMS-REST SPA</strong>
            </span>
        </div>
        <ul class="nav navbar-nav navbar-right">
            <li><span id="username" class="navbar-text"></span></li>
            <li><a class="btn btn-default" onclick="keycloak.logout()">Logout</a></li>
        </ul>
    </div>
</nav>
<div class="container-flued">
    <div class="col col-sm-3">
        <div>
            <h4>My tasks</h4>
            <div id="assigned-tasks" class="list-group"></div>
        </div>
        <hr/>
        <div>
            <h4>Available tasks</h4>
            <div id="available-tasks" class="list-group"></div>
        </div>
        <hr/>
        <div>
            <h4>Processes available to start</h4>
            <div id="startable-processes" class="list-group"></div>
        </div>
        <hr/>
    </div>
    <div id="current-form" class="col-sm-5">
        <div class="row">
            <div id="error" class="alert alert-danger" style="display: none;"></div>
            <div class="col">
                <h3 class="title" style="margin-top: 0"></h3>
                <div id="formio"></div>
            </div>
        </div>
    </div>
</div>
</body>

<script type="text/javascript">

    let fileStorageUrl = '<%=FILE_STORAGE_URL%>';
    let bpmsRestApi = '<%=BPMS_REST_API%>';
    let keycloakConfig = {
        'url': '<%=KEYCLOAK_SERVER_URL%>',
        'realm': '<%=KEYCLOAK_REALM%>',
        'clientId': '<%=KEYCLOAK_CLIENT_ID%>'
    };

    let keycloak = Keycloak(keycloakConfig);
    keycloak.init({onLoad: 'login-required', "checkLoginIframe": false})
        .success(function() {
            loadProcessesAndTasks();
            keycloak.loadUserInfo()
                .success(info => {
                    let fullUserName = getFullUserName(info.preferred_username);
                    $('#username').html('<strong>Welcome, ' + fullUserName + '!</strong>')
            })
        })
        .error(function(e) {
            alert('Authentication error ' + e);
        });
</script>
</html>