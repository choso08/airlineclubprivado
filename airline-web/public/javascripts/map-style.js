var currentStyles
var currentTypes
var pathOpacityByStyle = {
    "dark" : {
        highlight : "0.8",
        normal : "0.4"
    },
    "light" : {
        highlight : "1.0",
        normal  : "0.8"
    }
}

/*
 * The map's light/dark and the game's light/dark used to be two unrelated
 * settings: one under Settings, one behind a small switch on the map itself.
 * Choosing dark therefore gave you a dark game with a bright white map until
 * you found the second control - which nobody should have to.
 *
 * So the map follows the game's colour theme. map.theme can pin it instead,
 * for anyone who genuinely wants a light map against a dark game.
 */

/** "light" or "dark" if the host pinned the map's theme, otherwise null. */
function mapThemePin() {
	var pin = window.OSM_DEFAULT_THEME
	return (pin === 'light' || pin === 'dark') ? pin : null
}

/** The game's own colour theme, as color-scheme.js keeps it. */
function gameColorTheme() {
	try {
		return (localStorage.getItem('theme') === 'light') ? 'light' : 'dark'
	} catch (e) {
		// Private browsing can refuse localStorage entirely.
		return 'dark'
	}
}

/** Put the map on a theme, and redraw the routes in that palette. */
function applyMapTheme(theme) {
	if (currentStyles === theme) {
		return
	}
	currentStyles = theme
	$.cookie('currentMapStyles', currentStyles)

	if (typeof map === 'undefined' || !map) {
		return  // not built yet; it will pick this up when it is
	}
	map.setOptions({styles: getMapStyles()})
	// Redrawing the routes in the new palette is a nicety; the theme has
	// already changed by this point. It throws on the sign-in page, where
	// there is no airline to draw routes for, and an uncaught error there
	// looks to a player as though the switch is broken.
	try {
		refreshLinks(false)
	} catch (e) {
		console.log('map theme changed, routes not redrawn: ' + e.message)
	}
}

/** Called by switchTheme() whenever the player changes the game's theme. */
function syncMapThemeWithGame() {
	if (mapThemePin()) {
		return
	}
	applyMapTheme(gameColorTheme())
}

function initStyles() {
	// The game's theme wins over whatever the map was last left on, so that
	// the one control under Settings is believable. A pinned map.theme is the
	// host saying otherwise, and that wins over both.
	currentStyles = mapThemePin() || gameColorTheme()
	$.cookie('currentMapStyles', currentStyles);
	console.log("onload " + currentStyles)

	console.log("onload cookie" + $.cookie('currentMapTypes'))
	if ($.cookie('currentMapTypes')) {
		currentTypes = $.cookie('currentMapTypes')
	} else {
		currentTypes = 'roadmap'
		$.cookie('currentMapTypes', currentTypes);
	}
	console.log("onload " + currentTypes)
}

function getMapStyles() {
	console.log("getting " + currentStyles)
	if (currentStyles == 'light') {
		return lightStyles
	} else {
		return darkStyles
	}
}

function getMapTypes() {
	console.log("getting " + currentTypes)
	return currentTypes
}

/*
 * The switch on the map. With the map following the game, this moves both -
 * two controls for one setting, rather than two settings that disagree. When
 * the host has pinned the map's theme it moves the map alone, since that is
 * what pinning it was for.
 */
function toggleMapLight() {
	var next = (currentStyles == 'dark') ? 'light' : 'dark'

	if (mapThemePin()) {
		applyMapTheme(next)
		return
	}

	// Go through the game's own control so the radio buttons under Settings,
	// localStorage and the page's data-theme all stay in step - the map is
	// then brought along by switchTheme().
	$('#switchDark').prop('checked', next === 'dark')
	$('#switchLight').prop('checked', next === 'light')
	if (typeof switchTheme === 'function') {
		switchTheme()
	} else {
		applyMapTheme(next)
	}
}

var darkStyles =  
   	[
   	  {
   	    "elementType": "geometry",
   	    "stylers": [
   	      {
   	        "color": "#1d2c4d"
   	      }
   	    ]
   	  },
   	  {
   	    "elementType": "labels.text.fill",
   	    "stylers": [
   	      {
   	        "color": "#8ec3b9"
   	      }
   	    ]
   	  },
   	  {
   	    "elementType": "labels.text.stroke",
   	    "stylers": [
   	      {
   	        "color": "#1a3646"
   	      }
   	    ]
   	  },
   	  {
   	    "featureType": "administrative.country",
   	    "elementType": "geometry.stroke",
   	    "stylers": [
   	      {
   	        "color": "#4b6878"
   	      }
   	    ]
   	  },
   	  {
   	    "featureType": "administrative.land_parcel",
   	    "elementType": "labels",
   	    "stylers": [
   	      {
   	        "visibility": "off"
   	      }
   	    ]
   	  },
   	  {
   	    "featureType": "administrative.land_parcel",
   	    "elementType": "labels.text.fill",
   	    "stylers": [
   	      {
   	        "color": "#64779e"
   	      }
   	    ]
   	  },
   	  {
   	    "featureType": "administrative.province",
   	    "elementType": "geometry.stroke",
   	    "stylers": [
   	      {
   	    	"visibility": "off"
   	      }
   	    ]
   	  },
   	  {
   	    "featureType": "landscape.man_made",
   	    "elementType": "geometry.stroke",
   	    "stylers": [
   	      {
   	        "color": "#334e87"
   	      }
   	    ]
   	  },
   	  {
   	    "featureType": "landscape.natural",
   	    "elementType": "geometry",
   	    "stylers": [
   	      {
   	        "color": "#023e58"
   	      }
   	    ]
   	  },
   	  {
   	    "featureType": "poi",
   	    "elementType": "geometry",
   	    "stylers": [
   	      {
   	        "color": "#283d6a"
   	      }
   	    ]
   	  },
   	  {
   	    "featureType": "poi",
   	    "elementType": "labels.text",
   	    "stylers": [
   	      {
   	        "visibility": "off"
   	      }
   	    ]
   	  },
   	  {
   	    "featureType": "poi",
   	    "elementType": "labels.text.fill",
   	    "stylers": [
   	      {
   	        "color": "#6f9ba5"
   	      }
   	    ]
   	  },
   	  {
   	    "featureType": "poi",
   	    "elementType": "labels.text.stroke",
   	    "stylers": [
   	      {
   	        "color": "#1d2c4d"
   	      }
   	    ]
   	  },
   	  {
   	    "featureType": "poi.business",
   	    "stylers": [
   	      {
   	        "visibility": "off"
   	      }
   	    ]
   	  },
   	  {
   	    "featureType": "poi.park",
   	    "elementType": "geometry.fill",
   	    "stylers": [
   	      {
   	        "color": "#023e58"
   	      }
   	    ]
   	  },
   	  {
   	    "featureType": "poi.park",
   	    "elementType": "labels.text.fill",
   	    "stylers": [
   	      {
   	        "color": "#3C7680"
   	      }
   	    ]
   	  },
   	  {
   	    "featureType": "road",
   	    "stylers": [
   	      {
   	        "visibility": "off"
   	      }
   	    ]
   	  },
   	  {
   	    "featureType": "road",
   	    "elementType": "geometry",
   	    "stylers": [
   	      {
   	        "color": "#304a7d"
   	      }
   	    ]
   	  },
   	  {
   	    "featureType": "road",
   	    "elementType": "labels.icon",
   	    "stylers": [
   	      {
   	        "visibility": "off"
   	      }
   	    ]
   	  },
   	  {
   	    "featureType": "road",
   	    "elementType": "labels.text.fill",
   	    "stylers": [
   	      {
   	        "color": "#98a5be"
   	      }
   	    ]
   	  },
   	  {
   	    "featureType": "road",
   	    "elementType": "labels.text.stroke",
   	    "stylers": [
   	      {
   	        "color": "#1d2c4d"
   	      }
   	    ]
   	  },
   	  {
   	    "featureType": "road.highway",
   	    "elementType": "geometry",
   	    "stylers": [
   	      {
   	        "color": "#2c6675"
   	      }
   	    ]
   	  },
   	  {
   	    "featureType": "road.highway",
   	    "elementType": "geometry.stroke",
   	    "stylers": [
   	      {
   	        "color": "#255763"
   	      }
   	    ]
   	  },
   	  {
   	    "featureType": "road.highway",
   	    "elementType": "labels.text.fill",
   	    "stylers": [
   	      {
   	        "color": "#b0d5ce"
   	      }
   	    ]
   	  },
   	  {
   	    "featureType": "road.highway",
   	    "elementType": "labels.text.stroke",
   	    "stylers": [
   	      {
   	        "color": "#023e58"
   	      }
   	    ]
   	  },
   	  {
   	    "featureType": "road.local",
   	    "elementType": "labels",
   	    "stylers": [
   	      {
   	        "visibility": "off"
   	      }
   	    ]
   	  },
   	  {
   	    "featureType": "transit",
   	    "stylers": [
   	      {
   	        "visibility": "off"
   	      }
   	    ]
   	  },
   	  {
   	    "featureType": "transit",
   	    "elementType": "labels.text.fill",
   	    "stylers": [
   	      {
   	        "color": "#98a5be"
   	      }
   	    ]
   	  },
   	  {
   	    "featureType": "transit",
   	    "elementType": "labels.text.stroke",
   	    "stylers": [
   	      {
   	        "color": "#1d2c4d"
   	      }
   	    ]
   	  },
   	  {
   	    "featureType": "transit.line",
   	    "elementType": "geometry.fill",
   	    "stylers": [
   	      {
   	        "color": "#283d6a"
   	      }
   	    ]
   	  },
   	  {
   	    "featureType": "transit.station",
   	    "elementType": "geometry",
   	    "stylers": [
   	      {
   	        "color": "#3a4762"
   	      }
   	    ]
   	  },
   	  {
   	    "featureType": "water",
   	    "elementType": "geometry",
   	    "stylers": [
   	      {
   	        "color": "#0e1626"
   	      }
   	    ]
   	  },
   	  {
   	    "featureType": "water",
   	    "elementType": "labels.text.fill",
   	    "stylers": [
   	      {
   	        "color": "#4e6d70"
   	      }
   	    ]
   	  }
   	]
var lightStyles = 
	[
	    {
	        "featureType": "road",
	        "stylers": [
	            {
	                "visibility": "off"
	            }
	        ]
	    },
	    {
	        "featureType": "transit",
	        "stylers": [
	            {
	                "visibility": "off"
	            }
	        ]
	    },
	    {
	        "featureType": "administrative.province",
	        "stylers": [
	            {
	                "visibility": "off"
	            }
	        ]
	    },
	    {
	        "featureType": "poi.park",
	        "elementType": "geometry",
	        "stylers": [
	            {
	                "visibility": "off"
	            }
	        ]
	    },
	    {
	        "featureType": "water",
	        "stylers": [
	            {
	                "color": "#004b76"
	            }
	        ]
	    },
	    {
	        "featureType": "landscape.natural",
	        "stylers": [
	            {
	                "visibility": "on"
	            },
	            {
	                "color": "#fff6cb"
	            }
	        ]
	    },
	    {
	        "featureType": "administrative.country",
	        "elementType": "geometry.stroke",
	        "stylers": [
	            {
	                "visibility": "on"
	            },
	            {
	                "color": "#7f7d7a"
	            },
	            {
	                "lightness": 10
	            },
	            {
	                "weight": 1
	            }
	        ]
	    }
	]