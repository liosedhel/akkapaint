if (window.console) {
  console.log("Welcome to your Play application's JavaScript!");
}

var exampleSocket = new WebSocket("ws://" + location.host + "/socket");

exampleSocket.onopen = function(evt) {  };

exampleSocket.onmessage = function (event) {
    var data = JSON.parse(event.data);
    for (var i = 0; i < data.pixels.length; i += 2) {
        sketcher.updateCanvasFromServer(data.pixels[i], data.pixels[i+1], data.color);
    }
}

var sketcher = null;

$(document).ready(function(e) {
	sketcher = new Sketcher( "sketch" );

    $(".jscolor").change(function(){
        sketcher.setColor("#" + $(this).val());
    });

    $(".brushSize").change(function(){
            sketcher.brush.width = Number($(this).val());
            sketcher.brush.height = Number($(this).val());
    });
});
