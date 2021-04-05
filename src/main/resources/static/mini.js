var ws;

function connect() {
    var url = "ws://127.0.0.1:8080/screen";
    ws = new WebSocket(url);
    ws.onopen = function () {
        console.debug('Info: WebSocket connection opened.');
    };
    ws.onmessage = function (event) {
        console.debug('Received: ' + event.data);
        var url = URL.createObjectURL(event.data);
        document.getElementById("img_show").src = url;
    };
    ws.onclose = function () {
        console.debug('Info: WebSocket connection closed.');
    };
}

function disconnect() {
    if (ws != null) {
        ws.close();
    }
    document.getElementById("img_show").src = null;
    console.debug("disconnect");
}