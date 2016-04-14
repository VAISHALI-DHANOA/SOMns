/* jshint -W097 */
"use strict";

var data = {};

/**
 * Dummy definition for IDE support. These objects are
 * generally read from the web socket connection.
 * @constructor
 */
function SourceSection() {
  this.firstIndex = 0;
  this.sourceText = "";
  this.shortName  = "";
}

/**
 * Dummy definition for IDE support. These objects are
 * generally read from the web socket connection.
 * @constructor
 */
function InvocationProfile() {
  this.invocations = 0;
  this.somTypes = [];
}

/**
 * Dummy definition for IDE support. These objects are
 * generally read from the web socket connection.
 * @constructor
 */
function CountingProfile() {
  this.count = 0;
}

function Actor(id, name, typeName) {
  this.id       = id;
  this.name     = name;
  this.typeName = typeName;
}

function MessageHistory(messages, actors) {
  this.messages = messages; // per actor
  this.actors   = actors;
}

function Message(id, sender, receiver) {
  this.id       = id;
  this.sender   = sender;
  this.receiver = receiver;
}

function createMockupActorHistory() {
  var actors = {
    a1 : new Actor("a1", "Master",   "Master"),
    a2 : new Actor("a2", "Producer", "Producer"),
    a3 : new Actor("a3", "Sort 1",   "Sort"),
    a4 : new Actor("a4", "Sort 2",   "Sort"),
    a5 : new Actor("a5", "Sort 3",   "Sort"),
    a6 : new Actor("a6", "Sort 4",   "Sort"),
    a7 : new Actor("a7", "Validator", "Validator")};

  var messages = {};
  messages.a2 = [new Message("ma2-0", "a1", "a2")];

  function create1000Messages(sender, rcvr) {
    var messages = [];
    for (var i = 0; i < 1000; i += 1) {
      messages.push(new Message("m-" + rcvr + "-" + i, sender, rcvr));
    }
    return messages;
  }
  messages.a3 = create1000Messages("a2", "a3");
  messages.a4 = create1000Messages("a3", "a4");
  messages.a5 = create1000Messages("a4", "a5");
  messages.a6 = create1000Messages("a5", "a6");
  messages.a7 = create1000Messages("a6", "a7");
  messages.a1 = [new Message("final", "a7", "a1")];

  return new MessageHistory(messages, actors);
}

function countNumberOfLines(str) {
  var cnt = 1;
  for (var i = 0; i < str.length; i++) {
    if (str[i] == "\n") {
      cnt += 1;
    }
  }
  return cnt;
}

function loadAndProcessFile(f) {
  var reader = new FileReader();
  reader.onload = (function(theFile) {

    function receivedFile(e) {
      var o = JSON.parse(e.target.result);
      data[theFile.name] = o;
      displaySources(o);
    }

    return receivedFile;
  })(f);

  reader.readAsText(f);
}

/**
 * @param {DragEvent} e
 */
function handleFileSelect(e) {
  e.stopPropagation();
  e.preventDefault();

  var files = e.dataTransfer.files; // FileList object.

  // files is a FileList of File objects. List some properties.
  var output = [];
  for (var i = 0; i < files.length; i++) {
    loadAndProcessFile(files[i]);
  }
}


var accessProfiles = [],
  maxAccessCount = -1;

function Breakpoint(source, line, lineNumSpan) {
  this.source      = source;
  this.line        = line;
  this.enabled     = false;
  this.lineNumSpan = lineNumSpan;
  this.checkbox    = null;
}

Breakpoint.prototype.toggle = function () {
  this.enabled = !this.enabled;
};

Breakpoint.prototype.isEnabled = function () {
  return this.enabled;
};

function handleDragOver(evt) {
  evt.stopPropagation();
  evt.preventDefault();
  evt.dataTransfer.dropEffect = 'copy'; // Explicitly show this is a copy.
}

function dbgLog(msg) {
  var tzOffset = (new Date()).getTimezoneOffset() * 60000; // offset in milliseconds
  var localISOTime = (new Date(Date.now() - tzOffset)).toISOString().slice(0,-1);

  $("#debugger-log").html(localISOTime + ": " + msg + "<br/>" + $("#debugger-log").html());
}

function Debugger() {
  this.suspended = false;
  this.lastSuspendEventId = null;
  this.sourceObjects = {};
  this.breakpoints = {};
}

Debugger.prototype.getSource = function (id) {
  for (var fileName in this.sourceObjects) {
    if (this.sourceObjects[fileName].id === id) {
      return this.sourceObjects[fileName];
    }
  }
  return null;
};

Debugger.prototype.addSources = function (msg) {
  for (var sId in msg.sources) {
    this.sourceObjects[msg.sources[sId].name] = msg.sources[sId];
  }
};

Debugger.prototype.getBreakpoint = function (source, line, clickedSpan) {
  if (!this.breakpoints[source]) {
    this.breakpoints[source] = {};
  }

  var bp = this.breakpoints[source][line];
  if (!bp) {
    bp = new Breakpoint(source, line, clickedSpan);
    this.breakpoints[source][line] = bp;
  }
  return bp;
};

function showFrame(frame, i, list) {
  var stackEntry = frame.methodName;
  if (frame.sourceSection) {
    stackEntry += ":" + frame.sourceSection.line + ":" + frame.sourceSection.column;
  }
  var entry = nodeFromTemplate("stack-frame-tpl");
  entry.setAttribute("id", "frame-" + i);

  var tds = $(entry).find("td");
  tds[0].innerHTML = stackEntry;
  list.appendChild(entry);
}

Debugger.prototype.setSuspended = function(eventId) {
  console.assert(!this.suspended);
  this.suspended = true;
  this.lastSuspendEventId = data.id;
};

Debugger.prototype.updateUIForContinuingExecution = function() {
  $(this.currentDomNode).removeClass("DbgCurrentNode");
};

Debugger.prototype.resume = function() {
  console.assert(this.suspended);

  this.updateUIForContinuingExecution();
  this.socket.send(JSON.stringify({
    action:'resume',
    suspendEvent: this.lastSuspendEventId}));
};
Debugger.prototype.pause = function() {
  console.assert(!this.suspended);

  this.socket.send(JSON.stringify({
    action:'pause',
    suspendEvent: this.lastSuspendEventId}));
};
Debugger.prototype.stop = function() {
  console.assert(!this.suspended);

  this.socket.send(JSON.stringify({
    action:'stop',
    suspendEvent: this.lastSuspendEventId}));
};

Debugger.prototype.stepInto = function() {
  console.assert(this.suspended);

  this.updateUIForContinuingExecution();
  this.socket.send(JSON.stringify({
    action:'stepInto',
    suspendEvent: this.lastSuspendEventId}));
};
Debugger.prototype.stepOver = function() {
  console.assert(this.suspended);

  this.socket.send(JSON.stringify({
    action:'stepOver',
    suspendEvent: this.lastSuspendEventId}));
};
Debugger.prototype.return = function() {
  console.assert(this.suspended);

  this.socket.send(JSON.stringify({
    action:'return',
    suspendEvent: this.lastSuspendEventId}));
};


/* globals View, Controller, VmConnection */
var ctrl;

function init() {
  var view = new View(),
    vmConnection = new VmConnection(),
    dbg = new Debugger();
  ctrl = new Controller(dbg, view, vmConnection);
  ctrl.toggleConnection();

  // Init drag and drop
  var fileDrop = document.getElementById('file-drop');
  fileDrop.addEventListener('dragover', handleDragOver,   false);
  fileDrop.addEventListener('drop',     handleFileSelect, false);
}

function blobToFile(blob, name) {
  blob.lastModifiedDate = new Date();
  blob.name = name;
  return blob;
}

function getFileObjectFromPath(pathOrUrl, callback) {
  var request = new XMLHttpRequest();
  request.open("GET", pathOrUrl);
  request.responseType = "blob";
  request.addEventListener('load', function () {
    callback(blobToFile(request.response, pathOrUrl));
  });
  request.send();
}

function loadStandardFile() {
  getFileObjectFromPath("highlight.json",
    function(file) {
      loadAndProcessFile(file);
    });
}
