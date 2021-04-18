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

function clickImage(event) {
    if (ws == null) {
        console.log("ws is null");
        return;
    }
    var img = document.getElementById("img_show");
    var realX = parseInt(event.offsetX / img.width * img.naturalWidth);
    var realY = parseInt(event.offsetY / img.height * img.naturalHeight);
    console.log("realX: %d, realY: %d", realX, realY);
    message = { "action": "swipe", "fromX": realX, "fromY": realY, "toX": realX, "toY": realY };
    ws.send(JSON.stringify(message));
}