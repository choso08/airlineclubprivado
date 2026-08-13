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

function initStyles() {
	console.log("onload cookie" + $.cookie('currentMapStyles'))
	if ($.cookie('currentMapStyles')) {
		currentStyles = $.cookie('currentMapStyles')
	} else {
		// First visit: take the server's map.theme setting. Upstream always
		// started dark, which is still the default, but a host who prefers
		// light maps should not have to tell every player to click the switch.
		currentStyles = (window.OSM_DEFAULT_THEME === 'light') ? 'light' : 'dark'
		$.cookie('currentMapStyles', currentStyles);
	}
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

function toggleMapLight() {
	if (currentStyles == 'dark') {
		currentStyles = 'light'
	} else {
		currentStyles = 'dark'
	}
	$.cookie('currentMapStyles', currentStyles);
	console.log($.cookie('currentMapStyles'))
	
	map.setOptions({styles: getMapStyles()});
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