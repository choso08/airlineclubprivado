var map
var airportMap
var markers
var baseMarkers = []
var activeAirline
var activeUser
var selectedLink
var currentTime
var currentCycle
var airlineColors = {}
var airlineLabelColors = {}
var polylines = []
var airports = undefined

$( document ).ready(function() {
	mobileCheck()
	$('#tutorialHtml').load('assets/html/tutorial.html')
    $('#noticeHtml').load('assets/html/notice.html', initNotices)
	populateNavigation()
    history.replaceState({"onclickFunction" : "showWorldMap()"}, null, "/") //set the initial state

	window.addEventListener('orientationchange', refreshMobileLayout)

    populateLookups()
	if ($.cookie('sessionActive')) {
		loadUser(false)
	} else {
		hideUserSpecificElements()
		refreshLoginBar()
		getAirports();
//		printConsole("Please log in")
        showAbout();
        refreshWallpaper()
	}

    registerEscape()
    updateAirlineColors()
	initTabGroup()

	populateTooltips()
	checkAutoplaySettings()

	
	if ($("#floatMessage").val()) {
		showFloatMessage($("#floatMessage").val())
	}
	$(window).scroll(function()
	{
  		$('#floatBackButton').animate({top: ($(window).scrollTop() + 100) + "px" },{queue: false, duration: 350});
	});

	$('#chattext').jemoji({
        folder : 'assets/images/emoji/'
        //btn:    $('#emojiButton') //button is buggy and hard to select (not categorized), lets not enable it now
    });

    Splitting();
    if (isIe()) {
        //remove all laser elements, as IE cannot handle it
        $(".laser").hide()
    }

//    startFirework(10000)

	//plotSeatConfigurationGauge($("#seatConfigurationGauge"), {"first" : 0, "business" : 0, "economy" : 220}, 220)
})

$(window).on('focus', function() {
    if (selectedAirlineId) {
        checkWebSocket(selectedAirlineId)
    }
})

function registerEscape() {
    $(document).keyup(function(e) {
         if (e.key === "Escape") { // escape key maps to keycode `27`
            var $topModal = $(".modal:visible").last()
            if ($topModal.length > 0) {
                closeModal($topModal)
            } else {
                closeAirportInfoPopup()
            }

            var $topMapControlPanel = $(".mapControlPanel:visible").last();
            if ($topMapControlPanel.length > 0) {
                closeMapControlPanelById($topMapControlPanel[0].id);
            }
        } else if (e.key === "Enter") {
            if ($('#confirmationModal').is(':visible')) {
                closeModal($('#confirmationModal'))
                executeConfirmationTarget()
            }
        }
    });
}

/**
 * Appropriately closes the mapControlPanel by element ID.
 * @param {string} elementId HTML ID of an element with a 'mapControlPanel' class.
 */
function closeMapControlPanelById(elementId) {
    switch (elementId) {
        case "linkHistoryControlPanel":
            hideLinkHistoryView();
            return;
        case "heatmapControlPanel":
            closeHeatmap();
            return;
        case "topAirportLinksPanel":
            hideAirportLinksView();
            return;
        default:
            console.log("Escape closing not supported for:", elementId);
            return;
	}
}


function mobileCheck() {
	if (isMobileDevice()) { //assume it's a less powerful device
		refreshMobileLayout()

		//turn off animation by default
		currentAnimationStatus = false

		//registerSwipe()
	}
}

function isMobileDevice() {
    return window.screen.availWidth < 1024
}

function refreshMobileLayout() {
	if (window.screen.availWidth < window.screen.availHeight) { //only toggle layout change if it's landscape
		$("#reputationLevel").hide()
    } else {
        $("#reputationLevel").show()
	}
	delete(map)
	//yike, what if we miss something...the list below is kinda random
	//initMap()
	if (airports) {
	    addMarkers(airports)
    }
	if (activeAirline) {
	    updateLinksInfo()
	    updateAirportMarkers(activeAirline)
    }
}

function showFloatMessage(message, timeout) {
	timeout = timeout || 3000
	$("#floatMessageBox").text(message)
	var centerX = $("#floatMessageBox").parent().width() / 2 - $("#floatMessageBox").width() / 2 
	$("#floatMessageBox").css({ top:"-=20px", left: centerX, opacity:100})
	$("#floatMessageBox").show()
	$("#floatMessageBox").animate({ top:"0px" }, "fast", function() {
		if (timeout > 0) {
			setTimeout(function() { 
				console.log("closing")
				$('#floatMessageBox').animate({ top:"-=20px",opacity:0 }, "slow", function() {
					$('#floatMessageBox').hide()
				})
			}, timeout)
		}
	})
	
	//scroll the message box to the top offset of browser's scroll bar
	$(window).scroll(function()
	{
  		$('#floatMessageBox').animate({top:$(window).scrollTop()+"px" },{queue: false, duration: 350});
	});
}

function refreshLoginBar() {
	if (!activeUser) {
		$("#loginDiv").show();
		$("#logoutDiv").hide();
	} else {
		$("#currentUserName").empty()
		$("#currentUserName").append(activeUser.userName + getUserLevelImg(activeUser.level))
		$("#logoutDiv").show();
        $("#loginDiv").hide();
	}
}


function loadUser(isLogin) {
	var ajaxCall = {
	  type: "POST",
	  url: "login",
	  success: function(user) {
		  if (user) {
		      closeAbout()
		      activeUser = user
			  $.cookie('sessionActive', 'true');
			  $("#loginUserName").val("")
			  $("#loginPassword").val("")

			  if (isLogin) {
			  	  showFloatMessage("Successfully logged in")
				  showAnnoucement()
			  } else {
			      // Sessions survive a restart now, so almost nobody arrives
			      // through the login form any more - and the panel used to
			      // open only for those who did, which quietly meant never.
			      // Show it when the version has changed instead, which is
			      // when there is actually something to read.
			      showAnnouncementIfNew()
			  }
              refreshWallpaper()
    		  refreshLoginBar()
//			  printConsole('') //clear console
			  getAirports()
			  showUserSpecificElements();
			  updateChatTabs()
			  initAdminActions()
			  
			  // The FullStory session recorder was removed from this instance, so
			  // FS no longer exists. This call was left behind and threw
			  // "FS is not defined" on every login from any hostname other than
			  // localhost - aborting the rest of this handler, which is what
			  // selects the airline and fills in the top status bar.
		  }
		  if (user.airlineIds.length > 0) {
			  selectAirline(user.airlineIds[0])
			  loadAllCountries() //load country again for relationship
			  //loadAllLogs()
			  addAirlineSpecificMapControls(map)
              initPrompts()
		  }
		  updateAirlineLabelColors()
		  $('.button.login').removeClass('loading')

	  },
	    error: function(jqXHR, textStatus, errorThrown) {
	    	if (jqXHR.status == 401) {
	    		showFloatMessage("Incorrect username or password")
	    	} else if (jqXHR.status == 400) {
	    		showFloatMessage("Session expired. Please log in again")
		    } else if (jqXHR.status == 403) {
	    		showFloatMessage("You have been banned for violating the game rules. Please contact the server owner.")
	    	} else {
	    	    showFloatMessage("Error logging in, error code " + jqXHR.status + ". Please try again, and tell the server owner if it keeps happening.")
	            console.log(JSON.stringify(jqXHR));
	            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
	    	}
	    	$('.button.login').removeClass('loading')
	    }
	}
	if (isLogin) {
		var userName = $("#loginUserName").val()
		var password = $("#loginPassword").val()
		ajaxCall.headers = {
			    "Authorization": "Basic " + btoa(userName + ":" + password)
		}

	}
	
	return $.ajax(ajaxCall);
}

function passwordLogin(e) {
	if (e.keyCode === 13) {  //checks whether the pressed key is "Enter"
		login()
	}
}

function login()  {
    $('.button.login').addClass('loading')
    loadUser(true)
}

function onGoogleLogin(googleUser) {
	var profile = googleUser.getBasicProfile();
    console.log('ID: ' + profile.getId()); // Do not send to your backend! Use an ID token instead.
	console.log('Name: ' + profile.getName());
	console.log('Image URL: ' + profile.getImageUrl());
	console.log('Email: ' + profile.getEmail()); // This is null if the 'email' scope is not present.
	loginType='plain'
}

function logout() {
	$.ajax
	({
	  type: "POST",
	  url: "logout",
	  async: false,
	  success: function(message) {
	    	console.log(message)
	    	activeUser = null
	    	activeAirline = null
	    	airlineLabelColors = {}
	    	hideUserSpecificElements()
	    	$.removeCookie('sessionActive')
	    	//refreshLoginBar()
	    	//showFloatMessage("Successfully logged out")
	    	location.reload();
	    },
	    error: function(jqXHR, textStatus, errorThrown) {
	            console.log(JSON.stringify(jqXHR));
	            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
	    }
	});
	
	removeMarkers()
}

function showUserSpecificElements() {
	$('.user-specific-tab').show()
	$('.topBarDetails').show()
	$('.topBarDetails').parent().removeClass('hide-empty') //hack to avoid empty floating div for modern layout
}

function hideUserSpecificElements() {
	$('.user-specific-tab').hide()
	$('.topBarDetails').hide()
	$('.topBarDetails').parent().addClass('hide-empty') //hack to avoid empty floating div for modern layout
}


function initMap() {
	initStyles()
  map = new google.maps.Map(document.getElementById('map'), {
	// Where the map opens. Upstream centres on the Pacific (20, 150.644),
	// which means dragging half way round the world on every visit if your
	// airline is anywhere else. Set by AIRLINE_MAP_DEFAULT_* in
	// game-settings.env.
	center: {lat: (window.MAP_DEFAULT_LAT !== undefined ? window.MAP_DEFAULT_LAT : 20),
	         lng: (window.MAP_DEFAULT_LNG !== undefined ? window.MAP_DEFAULT_LNG : 150.644)},
   	zoom : (window.MAP_DEFAULT_ZOOM !== undefined ? window.MAP_DEFAULT_ZOOM : 2),
   	minZoom : 2,
   	gestureHandling: 'greedy',
   	styles: getMapStyles(),
	mapTypeId: getMapTypes(),
   	restriction: {
                latLngBounds: { north: 85, south: -85, west: -180, east: 180 },
              }
  });
	
  google.maps.event.addListener(map, 'zoom_changed', function() {
	    var zoom = map.getZoom();
	    // iterate over markers and call setVisible
	    $.each(markers, function( key, marker ) {
	        marker.setVisible(isShowMarker(marker, zoom));
	    })
  });
  
  google.maps.event.addListener(map, 'maptypeid_changed', function() { 
		var mapType = map.getMapTypeId();
		$.cookie('currentMapTypes', mapType);
  });

  addCustomMapControls(map)
}

function addCustomMapControls(map) {
//			<div id="toggleMapChristmasButton" class="googleMapIcon" onclick="toggleChristmasMarker()" align="center" style="display: none; margin-bottom: 10px;"><span class="alignHelper"></span><img src='@routes.Assets.versioned("images/icons/bauble.png")' title='Merry Christmas!' style="vertical-align: middle;"/></div>-->
//			<div id="toggleMapAnimationButton" class="googleMapIcon" onclick="toggleMapAnimation()" align="center" style="display: none; margin-bottom: 10px;"><span class="alignHelper"></span><img src='@routes.Assets.versioned("images/icons/arrow-step-over.png")' title='toggle flight marker animation' style="vertical-align: middle;"/></div>-->
//			<div id="toggleMapLightButton" class="googleMapIcon" onclick="toggleMapLight()" align="center" style="display: none;"><span class="alignHelper"></span><img src='@routes.Assets.versioned("images/icons/switch.png")' title='toggle dark/light themed map' style="vertical-align: middle;"/></div>-->
   var toggleMapChristmasButton = $('<div id="toggleMapChristmasButton" class="googleMapIcon" onclick="toggleChristmasMarker()" align="center" style="display: none; margin-bottom: 10px;"><span class="alignHelper"></span><img src="assets/images/icons/bauble.png" title=\'Merry Christmas!\' style="vertical-align: middle;"/></div>')
   var toggleMapAnimationButton = $('<div id="toggleMapAnimationButton" class="googleMapIcon" onclick="toggleMapAnimation()" align="center" style="margin-bottom: 10px;"><span class="alignHelper"></span><img src="assets/images/icons/arrow-step-over.png" title=\'toggle flight marker animation\' style="vertical-align: middle;"/></div>')
   var toggleChampionButton = $('<div id="toggleChampionButton" class="googleMapIcon" onclick="toggleChampionMap()" align="center"  style="margin-bottom: 10px;"><span class="alignHelper"></span><img src="assets/images/icons/crown.png" title=\'toggle champion\' style="vertical-align: middle;"/></div>')
   var toggleMapLightButton = $('<div id="toggleMapLightButton" class="googleMapIcon" onclick="toggleMapLight()" align="center" style=""><span class="alignHelper"></span><img src="assets/images/icons/switch.png" title=\'toggle dark/light themed map\' style="vertical-align: middle;"/></div>')


  toggleMapLightButton.index = 1
  toggleMapAnimationButton.index = 2
  toggleChampionButton.index = 3
  toggleMapChristmasButton.index = 5


  if ($("#map").height() > 500) {
    map.controls[google.maps.ControlPosition.RIGHT_BOTTOM].push(toggleMapLightButton[0]);
    map.controls[google.maps.ControlPosition.RIGHT_BOTTOM].push(toggleMapAnimationButton[0]);
    map.controls[google.maps.ControlPosition.RIGHT_BOTTOM].push(toggleChampionButton[0])
    //map.controls[google.maps.ControlPosition.RIGHT_BOTTOM].push(toggleHeatmapButton[0])

    if (christmasFlag) {
       map.controls[google.maps.ControlPosition.RIGHT_BOTTOM].push(toggleMapChristmasButton[0]);
       toggleMapChristmasButton.show()
    }

  } else {
    map.controls[google.maps.ControlPosition.LEFT_BOTTOM].push(toggleMapLightButton[0]);
    map.controls[google.maps.ControlPosition.LEFT_BOTTOM].push(toggleMapAnimationButton[0]);
    map.controls[google.maps.ControlPosition.LEFT_BOTTOM].push(toggleChampionButton[0])
    //map.controls[google.maps.ControlPosition.LEFT_BOTTOM].push(toggleHeatmapButton[0])

    if (christmasFlag) {
       map.controls[google.maps.ControlPosition.LEFT_BOTTOM].push(toggleMapChristmasButton[0]);
    }
  }
}

function addAirlineSpecificMapControls(map) {
    var toggleHeatmapButton = $('<div id="toggleMapHeatmapButton" class="googleMapIcon" onclick="toggleHeatmap()" align="center"  style="margin-bottom: 10px;"><span class="alignHelper"></span><img src="assets/images/icons/table-heatmap.png" title=\'toggle heatmap\' style="vertical-align: middle;"/></div>')

    toggleHeatmapButton.index = 4

    if ($("#map").height() > 500) {
        map.controls[google.maps.ControlPosition.RIGHT_BOTTOM].insertAt(3, toggleHeatmapButton[0])
     } else {
        map.controls[google.maps.ControlPosition.LEFT_BOTTOM].insertAt(3, toggleHeatmapButton[0])
    }
}

function LinkHistoryControl(controlDiv, map) {
    // Set CSS for the control border.
    var controlUI = document.createElement('div');
    controlUI.style.backgroundColor = '#fff';
    controlUI.style.border = '2px solid #fff';
    controlUI.style.borderRadius = '3px';
    controlUI.style.boxShadow = ' 0px 1px 4px -1px rgba(0,0,0,.3)';
    //controlUI.style.cursor = 'pointer';
    controlUI.style.marginBottom = '22px';
    controlUI.style.textAlign = 'center';
    controlUI.title = 'Click to recenter the map';
    controlUI.style.padding = '8px';
    controlUI.style.margin= '10px';
    controlUI.style.verticalAlign = 'middle';
    controlDiv.appendChild(controlUI);
    

    $(controlUI).append("<img src='assets/images/icons/24-arrow-180.png' class='button' onclick='toggleLinkHistoryView(false)'  title='Toggle passenger history view'/>")
    // Set CSS for the control interior.
    $(controlUI).append("<span id='linkHistoryText' style='color: rgb(86, 86, 86); font-family: Roboto, Arial, sans-serif; font-size: 11px;'></span>");
    
    $(controlUI).append("<img src='assets/images/icons/24-arrow.png' class='button' onclick='toggleLinkHistoryView(false)'  title='Toggle passenger history view'/>")

    // Setup the click event listeners: simply set the map to Chicago.
    controlUI.addEventListener('click', function() {
      map.setCenter(chicago);
    });

  }


function updateAllPanels(airlineId) {
	updateAirlineInfo(airlineId)
	
//	if (activeAirline) {
//		if (christmasFlag) {
//		    printConsole("Breaking news - Santa went missing!!! Whoever finds Santa will be rewarded handsomely! He could be hiding in one of the size 6 or above airports! View the airport page to track him down!", true, true)
//		}
//
//	}
	
}

//does not remove or add any components
function refreshPanels(airlineId) {
	$.ajax({
		type: 'GET',
		url: "airlines/" + airlineId,
	    contentType: 'application/json; charset=utf-8',
	    dataType: 'json',
	    async: false,
	    success: function(airline) {
	    	activeAirline = airline

	    	// Each of these is refreshed on its own. They used to run one after
	    	// another in a single block, so anything that threw - a map redraw,
	    	// a panel with unexpected data - silently cancelled every refresh
	    	// after it. The symptom is the game appearing to stop updating when
	    	// a cycle passes, with no error anywhere a player would look.
	    	var steps = [
	    		function() { refreshTopBar(airline) },
	    		function() { if ($("#worldMapCanvas").is(":visible")) refreshLinks() },
	    		function() {
	    			if ($("#linkDetails").is(":visible") || $("#linkDetails").hasClass("active")) {
	    				refreshLinkDetails(selectedLink)
	    			}
	    		},
	    		function() { if ($("#linksCanvas").is(":visible")) loadLinksTable() },
	    		// The four above are the screens upstream bothered to refresh. On
	    		// a private server people sit on the others for a whole cycle and
	    		// see nothing change, which reads as the game being stuck.
	    		function() { refreshVisibleCanvas() }
	    	]
	    	steps.forEach(function(step) {
	    		try {
	    			step()
	    		} catch (e) {
	    			console.log("refresh step failed, continuing: " + e.message)
	    		}
	    	})
	    },
	    error: function(jqXHR, textStatus, errorThrown) {
	            console.log(JSON.stringify(jqXHR));
	            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
	    }
	});
}

/**
 * Re-run the loader for whatever screen the player is looking at.
 *
 * Called after every completed cycle. Each entry is a visible-canvas check and
 * the function that fills it; anything not listed simply is not refreshed,
 * which is the old behaviour rather than a new failure. Guarded individually
 * so one missing function cannot stop the others.
 */
function refreshVisibleCanvas() {
    var screens = [
        ["#airlineCanvas",      function() { if (typeof showAirlineCanvas === 'function') showAirlineCanvas() }],
        ["#airplaneCanvas",     function() { if (typeof loadAirplaneList === 'function') loadAirplaneList() }],
        ["#airportCanvas",      function() { if (typeof loadAirportDetails === 'function' && typeof activeAirport !== 'undefined' && activeAirport) loadAirportDetails(activeAirport.id) }],
        ["#departuresCanvas",   function() { if (typeof loadDepartures === 'function') loadDepartures($('#airportPopupId').val()) }],
        ["#rankingCanvas",      function() { if (typeof loadRankings === 'function') loadRankings() }],
        ["#allianceCanvas",     function() { if (typeof loadAllianceDetails === 'function') loadAllianceDetails() }],
        ["#officeCanvas",       function() { if (typeof showOfficeCanvas === 'function') showOfficeCanvas() }],
        ["#oilCanvas",          function() { if (typeof loadOilCanvas === 'function') loadOilCanvas() }],
        ["#logCanvas",          function() { if (typeof loadAllLogs === 'function') loadAllLogs() }]
    ]

    screens.forEach(function(entry) {
        try {
            if ($(entry[0]).is(":visible")) entry[1]()
        } catch (e) {
            console.warn("could not refresh " + entry[0] + ": " + e.message)
        }
    })
}

var totalmillisecPerWeek = 7 * 24 * 60 * 60 * 1000

/**
 * How much in-game time one cycle is worth, in milliseconds.
 *
 * A cycle has always been exactly one week: the clock crosses a whole week
 * while the cycle runs, and the date advances seven days when it ends. At
 * seven real minutes to the cycle that is a day every minute, which is a lot
 * of calendar for a game people play an evening of.
 *
 * ui.gameTimeSpeed scales it without touching the simulation, which still
 * thinks in weeks and pays out weekly. At 0.5 the clock and the calendar move
 * at half the pace and a game week takes two cycles - the flights on the map
 * slow to match, since they are drawn against this same clock.
 *
 * It stays continuous either way: where one cycle's clock ends is where the
 * next one's begins, so the date never jumps.
 */
function gameTimePerCycle() {
	var speed = parseFloat(window.GAME_TIME_SPEED)
	if (isNaN(speed) || speed <= 0) {
		speed = 1
	}
	return totalmillisecPerWeek * speed
}
var refreshInterval = 5000 //every 5 second
var hasTickEstimation = false
var days = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];

var currentTickTimer
var tickTimerCreator

/*
 * The in-game clock, readable from anywhere.
 *
 * updateTime() already works this out to print the date in the header, but it
 * kept the pieces to itself. The flight animation on the map needs the same
 * clock - a flight has to leave at its scheduled hour and land duration
 * minutes later, and "its scheduled hour" means nothing without the clock the
 * rest of the game is showing.
 *
 * One week of game time passes per cycle, so this also decides how fast the
 * aircraft cross the map: a slower game is a slower flight, with no separate
 * animation speed to keep in step.
 */
var gameClockAnchorWall = null   // real time when we last knew the game time
var gameClockAnchorGame = null   // game time at that moment
var gameClockMultiplier = null   // game milliseconds per real millisecond

/** Current in-game time in milliseconds, or null before the first tick. */
function currentGameTime() {
    if (gameClockAnchorWall === null) return null
    return gameClockAnchorGame + (new Date().getTime() - gameClockAnchorWall) * gameClockMultiplier
}

/** Minutes since the start of the in-game week: 0 is Sunday 00:00. */
function currentGameWeekMinute() {
    var now = currentGameTime()
    if (now === null) return null
    var date = new Date(now)
    return date.getDay() * 24 * 60 + date.getHours() * 60 + date.getMinutes()
}

function updateTime(cycle, fraction, cycleDurationEstimation) {
	$(".currentTime").attr("title", "Current Cycle: " + cycle)
	//The in-game date is just "cycle 0 = the epoch", so a fresh world starts in
	//January 1970 and the clock reads as 1970 + one week per cycle. The offset
	//lets a private instance start in a year of its choosing instead; it is
	//purely cosmetic, since nothing in the simulation reads the date.
	gameTimeStart = (cycle + fraction) * gameTimePerCycle() + (window.GAME_START_EPOCH || 0)

    var initialDurationTillNextTick
	if (cycleDurationEstimation > 0) { //update incrementPerInterval
	    initialDurationTillNextTick = cycleDurationEstimation * (1 - fraction)
	    hasTickEstimation = true
	}

	var wallClockStart = new Date()
	gameClockAnchorWall = wallClockStart.getTime()
	gameClockAnchorGame = gameTimeStart

	//how much wall clock duration should be multiplied as game time duration
	//
	//The estimate arrives over the websocket a moment after the page loads.
	//Until then fall back to this instance's CONFIGURED cycle length rather
	//than a hardcoded 30 minutes - otherwise a server running 5 minute cycles
	//shows a clock crawling at a sixth of the real speed until the first
	//message lands.
	var configuredCycleMillis = (window.GAME_CYCLE_SECONDS || 1800) * 1000
	var timeMultiplier = cycleDurationEstimation > 0 ?
	    gameTimePerCycle() / cycleDurationEstimation :
		gameTimePerCycle() / configuredCycleMillis
	gameClockMultiplier = timeMultiplier


	if (currentTickTimer) {
	    clearInterval(currentTickTimer)
	}


    var updateTimerFunction = function() {
        var currentWallClock = new Date()
        var wallClockDurationSinceStart = currentWallClock.getTime() - wallClockStart.getTime()

        var durationTillNextTick = initialDurationTillNextTick - wallClockDurationSinceStart

        var currentGameTime = gameTimeStart + wallClockDurationSinceStart * timeMultiplier
        var currentGameDate = new Date(currentGameTime)
        $(".currentTime").text("(" + days[currentGameDate.getDay()] + ") " + padBefore(currentGameDate.getMonth() + 1, "0", 2) + '/' + padBefore(currentGameDate.getDate(), "0", 2) +  " " + padBefore(currentGameDate.getHours(), "0", 2) + ":00")

        if (hasTickEstimation) {
          var minutesLeft = Math.round(durationTillNextTick / 1000 / 60)
          if (minutesLeft <= 0) {
              $(".nextTickEstimation").text("Very soon")
          } else if (minutesLeft == 1) {
              $(".nextTickEstimation").text("1 minute")
          } else {
              $(".nextTickEstimation").text(minutesLeft + " minutes")
          }
        }
    }
    tickTimerCreator = function() {
        updateTimerFunction()
        var newTimer = setInterval(updateTimerFunction, refreshInterval);
        return newTimer
    }

	currentTickTimer = tickTimerCreator()
}


// Handle tab visibility change
document.addEventListener('visibilitychange', function () {
    clearInterval(currentTickTimer);
    if (!document.hidden && tickTimerCreator) {
        console.log("Recreating tick timer!")
        currentTickTimer = tickTimerCreator()
    }
});


//function printConsole(message, messageLevel, activateConsole, persistMessage) {
//	messageLevel = messageLevel || 1
//	activateConsole = activateConsole || false
//	persistMessage = persistMessage || false
//	var messageClass
//	if (messageLevel == 1) {
//		messageClass = 'actionMessage'
//	} else {
//		messageClass = 'errorMessage'
//	}
//
//	if (message == '') { //try to clear message, check if there was a persistent message
//		var previousMessage = $('#console #consoleMessage').data('persistentMessage')
//		if (previousMessage) {
//			message = previousMessage
//		}
//	}
//
//	if (persistMessage) {
//		$('#console #consoleMessage').data('persistentMessage', message)
//	}
//	var consoleVisible = $('#console #consoleMessage').is(':visible')
//
//	if (consoleVisible) {
//		$('#console #consoleMessage').fadeOut('slow', function() { //fade out and reset positions
//			$('#console #consoleMessage').text(message)
//			$('#console #consoleMessage').removeClass().addClass(messageClass)
//			$('#console #consoleMessage').fadeIn('slow')
//		})
//	} else {
//		$('#console #consoleMessage').text(message)
//		$('#console #consoleMessage').removeClass().addClass(messageClass)
//		if (activateConsole) {
//			$('#console #consoleMessage').fadeIn('slow')
//		}
//	}
//}
//
//function toggleConsoleMessage() {
//	if ($('#console #consoleMessage').is(':visible')) {
//		$('#console #consoleMessage').fadeOut('slow')
//	} else {
//		$('#console #consoleMessage').fadeIn('slow')
//	}
//}

function showWorldMap() {
	setActiveDiv($('#worldMapCanvas'));
	highlightTab($('.worldMapCanvasTab'))
	$('#sidePanel').appendTo($('#worldMapCanvas'))
	//closeAirportInfoPopup()
	if (selectedLink) {
		selectLinkFromMap(selectedLink, !activeAirportPopupInfoWindow) //do not refocus if there's a popup, stay where it is
	}
	checkTutorial('worldMap')
}

//switch to map view w/o considering leaving current tab
function switchMap() {
    var mapCanvas = $('#worldMapCanvas')
    var existingActiveDiv = mapCanvas.siblings(":visible").filter(function (index) {
		return $(this).css("clear") != "both"
	})
    if (existingActiveDiv.length > 0) {
        existingActiveDiv.fadeOut(200, function() {
            mapCanvas.fadeIn(200)
        })
    }
}

/**
 * The panel, but only when this build is one the player has not seen.
 *
 * A changelog that appears on every visit is a changelog nobody reads. This
 * one appears when the version it describes has changed, and then not again.
 */
function showAnnouncementIfNew() {
    $.ajax({
        type: 'GET', url: '/version', dataType: 'json',
        success: function(info) {
            var version = info && info.version
            if (!version || version === 'unknown') return
            var seen = null
            try { seen = window.localStorage.getItem('seenVersion') } catch (e) { /* private mode */ }
            if (seen === version) return
            try { window.localStorage.setItem('seenVersion', version) } catch (e) { /* ignore */ }
            showAnnoucement()
        },
        error: function() { /* no version file - say nothing */ }
    })
}

function showAnnoucement() {
    // Version and changelog, written by scripts/write-version.sh at update
    // time and served from /version. Failure here must not stop the panel
    // from opening - it is informational.
    $.ajax({
        type: 'GET', url: '/version', dataType: 'json',
        success: function(info) {
            if (info.version && info.version !== 'unknown') {
                $('#versionLine').text('Version ' + info.version + (info.date ? '  -  ' + info.date : ''))
            }
            var changes = info.changes || []
            $('#changelogList').empty()
            if (changes.length > 0) {
                $('#changelogHeading').show()
                changes.forEach(function(change) {
                    $('#changelogList').append(
                        $('<li class="label" style="padding: 5px;"></li>').text('- ' + change))
                })
            } else {
                $('#changelogHeading').hide()
            }
        },
        error: function() { /* no version file - leave the panel as it is */ }
    })

	// Get the modal
	var modal = $('#announcementModal')
	// Get the <span> element that closes the modal
	$('#announcementContainer').empty()
	$('#announcementContainer').load('assets/html/announcement.html')

	modal.fadeIn(1000)
}

function populateTooltips() {
    //scan for all tooltips
    $.each($(".tooltip"), function() {
        var htmlSource = $(this).data("html")
        if (htmlSource) { //then load the html, otherwise leave it alone (older tooltips)
            $(this).empty()
            $(this).load("assets/html/tooltip/" + htmlSource + ".html")
        }
    })

    populateDelegatesTooltips()
    populateDataPropertyTooltips()
}
function populateDelegatesTooltips() {
    var $html = $("<div></div>")
    $html.append("<p>Gained by leveling up your airline. Airline grade is determined by reputation points.</p>")
    $html.append("<p>Delegates conduct various tasks, such as Flight negotiations, Country relationship improvements, Advertisement campaigns etc.</p>")

    addTooltipHtml($('.delegatesTooltip'), $html, {'width' : '350px'})
}

function populateDataPropertyTooltips() {
    $('[data-tooltip-text]').each(function(index) {
        var width = Math.min(400, $(this).data('tooltipText').length * 5) //approximate width
        var css = { width : width + 'px'}
        addTooltip($(this), $(this).data('tooltipText'), css)
    })

}

var airlineGradeLookup
function populateLookups() {
    loadAllCountries()
    $.ajax({
		type: 'GET',
		url: "lookups",
	    contentType: 'application/json; charset=utf-8',
	    dataType: 'json',
	    async: false,
	    success: function(result) {
	    	airlineGradeLookup = result.airlineGradeLookup
	    },
	    error: function(jqXHR, textStatus, errorThrown) {
	            console.log(JSON.stringify(jqXHR));
	            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
	    }
	});
}

function showTutorial() {
	// Get the modal
	var modal = $('#tutorialModal')
	modal.fadeIn(1000)
}

function promptConfirm(prompt, targetFunction, param) {
	$('#confirmationModal .confirmationButton').data('targetFunction', targetFunction)
	if (typeof param != 'undefined') {
		$('#confirmationModal .confirmationButton').data('targetFunctionParam', param)
	}
	$('#confirmationPrompt').html(prompt)
	$('#confirmationModal').fadeIn(200)
}

function executeConfirmationTarget() {
	var targetFunction = $('#confirmationModal .confirmationButton').data('targetFunction')
	var targetFunctionParam = $('#confirmationModal .confirmationButton').data('targetFunctionParam')
	if (typeof targetFunctionParam != 'undefined') {
		targetFunction(targetFunctionParam) 
	} else {
		targetFunction()
	}
}

function promptSelection(question, choices, targetFunction) {
	$('#selectionModal .question').text(question)
	$('#selectionModal .selections').empty()
	$.each(choices, function(index, choice) {
	    var $selectionButton = $('<div class="button">' + choice + '</div>')
        $('#selectionModal .selections').append($selectionButton)
        $selectionButton.click(function() {
            targetFunction(choice)
            closeModal($('#selectionModal'))
        })
	})

	$('#selectionModal').fadeIn(200)
}


function updateAirlineColors() {
	var url = "colors"
    $.ajax({
		type: 'GET',
		url: url,
	    contentType: 'application/json; charset=utf-8',
	    dataType: 'json',
	    success: function(result) {
	    	airlineColors = result
	    },
        error: function(jqXHR, textStatus, errorThrown) {
	            console.log(JSON.stringify(jqXHR));
	            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
	    }
	});
}

function updateAirlineLabelColors(callback) {
    //get airline label color
    airlineLabelColors = {}
    $.ajax({
            type: 'GET',
            url: "airlines/" + activeAirline.id + "/airline-label-colors",
            contentType: 'application/json; charset=utf-8',
            dataType: 'json',
            success: function(result) {
                $.each(result, function(index, entry) {
                    airlineLabelColors[entry[0]] = entry[1]
                })
                if (callback) {
                    callback()
                }
            },
            error: function(jqXHR, textStatus, errorThrown) {
                    console.log(JSON.stringify(jqXHR));
                    console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
            }
    });

}

function assignAirlineColors(dataSet, colorProperty) {
	$.each(dataSet, function(index, entry) {
		if (entry[colorProperty]) {
			var airlineColor = airlineColors[entry[colorProperty]]
			if (airlineColor) {
				entry.color = airlineColor
			}
		}
	})
}

function populateNavigation(parent) { //change all the tabs to do fake url
    if (!parent) {
        parent = $(":root")
    }

    parent.find('[data-link]').andSelf().filter('[data-link]').each(function() {
        $(this).off('click.nav')

        var path = $(this).data("link") != "/" ? ("nav-" + $(this).data("link")) : "/"

        var onbackFunction = $(this).attr("onback") //prefer onback function, so we can pass a flag
        if (onbackFunction !== undefined) {
            $(this).on('click.nav', function() {
                history.pushState({ "onbackFunction" : onbackFunction}, null, path);
            })
        } else {
            var onclickFunction = $(this).attr("onclick")

            //console.log(path + " " + onclickFunction)

            $(this).on('click.nav', function() {
                history.pushState({ "onclickFunction" : onclickFunction}, null, path);
            })
        }
//
//        if (onclickFunction) {
//            eval(onclickFunction)
//        }
    })
}

let tabGroupState = {}

function showTabGroup() {
    if (tabGroupState.hideTimeout) {
        clearTimeout(tabGroupState.hideTimeout)
        tabGroupState.hideTimeout = undefined
    }
    $('#tabGroup').fadeIn(200)
}

function hideTabGroup(waitDuration) {
    if (tabGroupState.hideTimeout) {
        clearTimeout(tabGroupState.hideTimeout)
    }
    var timeout = setTimeout(() => $('#tabGroup').fadeOut(500), waitDuration ? waitDuration : 2000)
    tabGroupState.hideTimeout = timeout
}

function initTabGroup() {
    //$('#tabGroup .left-tab').bind('mouseout', () => { console.log('out'); hideTabGroup })
    //$("#tabGroup").mouseenter(() => showTabGroup()).mouseleave(() => { console.log('out'); hideTabGroup() })


    $("#tabGroup .tab-icon").on('mouseenter touchstart', function() {
        $(this).closest('.left-tab').find('.label').show();
    });

    $("#tabGroup .tab-icon").on('mouseleave touchend', function() {
        $(this).closest('.left-tab').find('.label').hide();
    });


//    $("#canvas").on( "swiperight", function( e ) {
//        if ($('#canvas')[0].scrollLeft == 0) {
//            showTabGroup()
//            hideTabGroup(5000)
//        }
//    });
    $("#canvas").on('touchstart', function(e) {
        var swipe = e.originalEvent.touches,
        startX = swipe[0].pageX;
        startY = swipe[0].pageY;
        $(this).on('touchmove', function(e) {
            var contact = e.originalEvent.touches,
            endX = contact[0].pageX,
            endY = contact[0].pageY,
            distanceX = endX - startX;
            distanceY = endY - startY
            if (Math.abs(distanceX) > Math.abs(distanceY) && distanceX > 30 && $('#main')[0].scrollLeft == 0) {
                showTabGroup()
                hideTabGroup(5000)
            }
        })
        .one('touchend', function() {
            $(this).off('touchmove touchend');
        });
    });

    $("#tabGroupCue").on('mouseenter touchstart',
        function() {
            showTabGroup()
        }
    )
    $("#tabGroupCue").on('mouseleave touchend',
        function() {
             hideTabGroup(5000)
        }
    )

    $("#tabGroup").on('mouseenter touchstart',
        function() {
            showTabGroup()
        }
    )
    $("#tabGroup").on('mouseleave touchend',
        function() {
             hideTabGroup()
        }
    )
}

function checkAutoplaySettings() {
    var autoplayEnabled = true
    if (localStorage.getItem("autoplay")){
      autoplayEnabled = localStorage.getItem("autoplay") === 'true'
    } else {
      localStorage.setItem('autoplay', autoplayEnabled)
    }
    $('input.autoplay').prop('checked', autoplayEnabled)
}

function toggleAutoplay() {
    localStorage.setItem('autoplay', $('input.autoplay').is(':checked'))
}

window.addEventListener('popstate', function(e) {
    if (e.state) {
        if (e.state.onbackFunction) {
            eval(e.state.onbackFunction) //onback has higher precedence
        } else if (e.state.onclickFunction) {
            eval(e.state.onclickFunction)
        }
    }
});
