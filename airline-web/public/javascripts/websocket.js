var port = window.location.port

var wsProtocol

if (window.location.protocol == "https:"){
	wsProtocol = "wss:"
	if (!port) {
		port = 443
	}
} else {
	wsProtocol = "ws:"
	if (!port) {
		port = 80
	}
}

var wsUri = wsProtocol + "//" +  window.location.hostname + ":" + port + "/wsWithActor";
var websocket;
var selectedAirlineId

$( document ).ready(function() {})

/*
 * This socket is how the page learns that a cycle has finished. Without it the
 * clock counts down to "Very soon" and then sits there for ever, and the only
 * way out is to reload by hand.
 *
 * It used to be given up on the moment it closed: onClose did nothing at all,
 * and the one reconnect attempt in the code ran only when the browser window
 * regained focus. Someone sitting and watching their airline - window focused,
 * never touched - was exactly the person who never got it back. Every restart
 * of the site, every sleeping laptop, every blip left the page permanently
 * deaf and looking frozen.
 *
 * So it reconnects on its own now, and checks itself even when nothing has
 * happened, because a TCP connection can die without the browser noticing:
 * readyState says OPEN, and no message ever arrives again.
 */
var RECONNECT_MIN_MS = 2000
var RECONNECT_MAX_MS = 30000
var SILENCE_LIMIT_MS = 90000   // the server pings well within this

var reconnectDelay = RECONNECT_MIN_MS
var reconnectTimer = null
var lastMessageAt = 0
var everConnected = false

function socketIsUsable() {
	return websocket && (websocket.readyState === WebSocket.OPEN || websocket.readyState === WebSocket.CONNECTING)
}

function checkWebSocket(selectedAirlineId) {
	if (!selectedAirlineId) return
	if (!socketIsUsable()) {
		connectWebSocket(selectedAirlineId)
		return
	}
	// Open but silent for too long: the connection is dead and the browser has
	// not worked it out. Close it and let the reconnect handle the rest.
	if (websocket.readyState === WebSocket.OPEN && lastMessageAt > 0 &&
			(new Date().getTime() - lastMessageAt) > SILENCE_LIMIT_MS) {
		console.log("no word from the server for a while - reconnecting")
		try { websocket.close() } catch (e) { /* already gone */ }
	}
}

function scheduleReconnect() {
	if (reconnectTimer || !selectedAirlineId) return
	console.log("reconnecting in " + Math.round(reconnectDelay / 1000) + "s")
	reconnectTimer = setTimeout(function() {
		reconnectTimer = null
		connectWebSocket(selectedAirlineId)
		// Back off, so a site that is down for a while is not hammered.
		reconnectDelay = Math.min(reconnectDelay * 2, RECONNECT_MAX_MS)
	}, reconnectDelay)
}

function connectWebSocket(airlineId) {
	websocket = new WebSocket(wsUri);
	websocket.onopen = function(evt) {
		sendMessage(airlineId)  //send airlineId to indicate we want to listen to messages for this airline Id
		console.log("successfully open socket on airline " + airlineId)
		lastMessageAt = new Date().getTime()
		reconnectDelay = RECONNECT_MIN_MS

		// Catch up. While the socket was down the world carried on, so the
		// screen is showing whatever it happened to be showing when the
		// connection went - which after a site restart is a whole cycle old.
		if (everConnected && typeof refreshPanels === 'function') {
			try {
				refreshPanels(airlineId)
			} catch (e) {
				console.log("could not refresh after reconnecting: " + e.message)
			}
		}
		everConnected = true
	};
	websocket.onclose = function(evt) { onClose(evt) };
	websocket.onmessage = function(evt) { onMessage(evt) };
	websocket.onerror = function(evt) { onError(evt) };
}

function initWebSocket(airlineId) {
	selectedAirlineId = airlineId
	connectWebSocket(airlineId)

	// Watch it even when the player does nothing. The focus handler in main.js
	// stays as well - coming back to the tab is a good moment to check - but it
	// cannot be the only one.
	setInterval(function() { checkWebSocket(selectedAirlineId) }, 15000)
}

function onClose(evt) {
	console.log("socket closed (" + (evt && evt.code) + ")")
	scheduleReconnect()
}
function onMessage(evt) { //right now the message is just the cycle #, so refresh the panels
	lastMessageAt = new Date().getTime()
	var json = JSON.parse(evt.data)
	if (json.ping) { //ok
	    console.debug("ping : " + json.ping)
        return
    }
	console.log("websocket received message : " + evt.data)
	
	if (json.messageType == "cycleInfo") { //update time
		updateTime(json.cycle, json.fraction, json.cycleDurationEstimation)
//	} else if (json.messageType == "cycleStart") { //update time
//		updateTime(json.cycle, 0)
	} else if (json.messageType == "cycleCompleted") {
		if (selectedAirlineId) {
			refreshPanels(selectedAirlineId)
		}
	} else if (json.messageType == "broadcastMessage") {
        queuePrompt("broadcastMessagePopup", json.message)
    } else if (json.messageType == "airlineMessage") {
        queuePrompt("airlineMessagePopup", json.message)
    } else if (json.messageType == "notice") {
        queueNotice(json)
    } else if (json.messageType == "tutorial") {
        queueTutorialByJson(json)
    } else if (json.messageType == "pendingAction") {
        handlePendingActions(json.actions)
    } else {
		console.warn("unknown message type " + evt.data)
	}
}  
function onError(evt) {
	console.log(evt)
} 

function sendMessage(message) {
	if (websocket && websocket.readyState === WebSocket.OPEN) {
		websocket.send(message);
	} else if (socketIsUsable()) {
		// Still connecting; give it a moment. Retrying against a socket that is
		// already closed would spin for ever, which is what happened before -
		// the reconnect below is what actually fixes it.
		setTimeout(function() { sendMessage(message) }, 1000)
	}
}