function adminAction(action, targetUserId, callback) {
 adminActionWithData(action, targetUserId, {}, callback)
}

function adminActionWithData(action, targetUserId, data, callback) {
	var url = "/admin-action/" + action + "/" + targetUserId
	var selectedAirlineId =  $("#rivalDetails .adminActions").data("airlineId")

	$.ajax({
		type: 'PUT',
		url: url,
	    data: JSON.stringify(data),
	    contentType: 'application/json; charset=utf-8',
	    dataType: 'json',
	    success: function(result) {
            showRivalsCanvas(selectedAirlineId)
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

function adminMultiAction(action, targetUserIds, callback) {
	var url = "/admin-multi-action/" + action
	var selectedAirlineId =  $("#rivalDetails .adminActions").data("airlineId")

    var data = {
        "userIds" : targetUserIds
     }
	$.ajax({
		type: 'PUT',
		url: url,
	    data: JSON.stringify(data),
	    contentType: 'application/json; charset=utf-8',
	    dataType: 'json',
	    success: function(result) {
	        showRivalsCanvas(selectedAirlineId)
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

function invalidateImage(imageType) {
	var url = "/admin/invalidate-image/" + activeAirportId +  "/" + imageType
	$.ajax({
		type: 'GET',
		url: url,
	    contentType: 'application/json; charset=utf-8',
	    dataType: 'json',
	    success: function(result) {
            showAirportDetails(activeAirportId)
	    },
        error: function(jqXHR, textStatus, errorThrown) {
	            console.log(JSON.stringify(jqXHR));
	            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
	    }
	});
}
function isAdmin() {
    return activeUser && activeUser.adminStatus
}

function isSuperAdmin() {
    return activeUser && activeUser.adminStatus === "SUPER_ADMIN"
}

function initAdminActions() {
    if (isAdmin()) {
        $(".adminActions").show()
    } else {
        $(".adminActions").hide()
    }
    if (isSuperAdmin()) {
        $(".superAdminActions").show()
    } else {
        $(".superAdminActions").hide()
    }
}


function showAdminActions(airline) {
    $("#rivalDetails .adminActions").data("userId", airline.userId)
    $("#rivalDetails .adminActions").data("airlineId", airline.id)
    $("#rivalDetails .adminActions .username").text(airline.username || '-')
    $("#rivalDetails .adminActions .userId").text(airline.userId)

    if (airline.userModifiers) {
        $("#rivalDetails .adminActions .userModifiers").text(airline.userModifiers.join(";"))
    } else {
        $("#rivalDetails .adminActions .userModifiers").text('-')
    }
    if (airline.airlineModifiers) {
        $("#rivalDetails .adminActions .airlineModifiers").text(airline.airlineModifiers.join(";"))
    } else {
        $("#rivalDetails .adminActions .airlineModifiers").text('-')
    }
    $("#rivalDetails .adminActions .ips").empty()
    $("#rivalDetails .adminActions .uuids").empty()
    $.ajax({
        type: 'GET',
        url: "/admin/user-ips/" + airline.userId,
        contentType: 'application/json; charset=utf-8',
        dataType: 'json',
        success: function(ips) {
            $.each(ips, function(index, ipEntry) {
                var ip = ipEntry[0]
                var occurrence = ipEntry[1]
                $("#rivalDetails .adminActions .ips").append("<div style='padding-right : 10px; float: left' class='clickable' onclick='showAirlinesByIp(\"" + ip + "\")'>" + ip + "(" + occurrence + ")</div>")
            })
            $("#rivalDetails .adminActions .ips").append("<div style='clear : both;'></div>")

        },
        error: function(jqXHR, textStatus, errorThrown) {
                console.log(JSON.stringify(jqXHR));
                console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
        }
    });
    $.ajax({
        type: 'GET',
        url: "/admin/user-uuids/" + airline.userId,
        contentType: 'application/json; charset=utf-8',
        dataType: 'json',
        success: function(result) {
            $.each(result, function(index, entry) {
                var uuid = entry[0]
                var occurrence = entry[1]
                $("#rivalDetails .adminActions .uuids").append("<div style='padding-right : 10px; float: left' class='clickable' onclick='showAirlinesByUuid(\"" + uuid + "\")'>" + uuid.substring(0, 8) + "(" + occurrence + ")</div>")
            })
            $("#rivalDetails .adminActions .uuids").append("<div style='clear : both;'></div>")

        },
        error: function(jqXHR, textStatus, errorThrown) {
                console.log(JSON.stringify(jqXHR));
                console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
        }
    });

}

function showAirlinesByIp(ip) {
    $.ajax({
        type: 'GET',
        url: "/admin/ip-airlines/" + ip,
        contentType: 'application/json; charset=utf-8',
        dataType: 'json',
        success: function(result) {
            $("#airlinesByIpModal .ip").text(ip)
            $("#airlinesByIpModal .airlineByIpTable div.table-row").remove()
            $.each(result, function(index, entry) {
               var $row = $("<div class='table-row'></div>")
               var airline = entry.airline
               var modifiersSpan = getUserModifiersSpan(entry.userModifiers) + getAirlineModifiersSpan(entry.airlineModifiers)
               if (modifiersSpan === "") {
                   modifiersSpan = "<span>-</span>"
               }
               $row.append("<div class='cell'><input type='checkbox' checked='checked' data-user-id='" + entry.userId + "' data-airline-id='" + entry.airlineId + "'></div>")
               $row.append("<div class='cell clickable' onclick='loadRivalDetails(null," + entry.airlineId + "); closeModal($(\"#airlinesByUuidModal\"))'>" + getAirlineLogoImg(entry.airlineId) +  entry.airlineName + "</div>")
               $row.append("<div class='cell'>" + (entry.hqAirport ? getAirportText(entry.hqAirport.city, entry.hqAirport.iata) : "-") + "</div>")
               $row.append("<div class='cell'>" + (entry.username || '-') + getUserLevelImg(entry.userLevel) + "</div>")
               $row.append("<div class='cell'>" + entry.userStatus + "</div>")
               $row.append("<div class='cell'>" + modifiersSpan + "</div>")
               $row.append("<div class='cell'>" + entry.lastUpdated + "</div>")
               $row.append("<div class='cell' align='right'>" + entry.occurrence + "</div>")
               $("#airlinesByIpModal .airlineByIpTable").append($row)
            })
            $("#airlinesByIpModal").fadeIn(500)
        },
        error: function(jqXHR, textStatus, errorThrown) {
                console.log(JSON.stringify(jqXHR));
                console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
        }
    });

}

function showAirlinesByUuid(uuid) {
    $.ajax({
        type: 'GET',
        url: "/admin/uuid-airlines/" + uuid,
        contentType: 'application/json; charset=utf-8',
        dataType: 'json',
        success: function(result) {
            $("#airlinesByUuidModal .uuid").text(uuid)
            $("#airlinesByUuidModal .airlineByUuidTable div.table-row").remove()
            $.each(result, function(index, entry) {
                var $row = $("<div class='table-row'></div>")
                var airline = entry.airline
                var modifiersSpan = getUserModifiersSpan(entry.userModifiers) + getAirlineModifiersSpan(entry.airlineModifiers)
               if (modifiersSpan === "") {
                   modifiersSpan = "<span>-</span>"
               }
                $row.append("<div class='cell'><input type='checkbox' checked='checked' data-user-id='" + entry.userId + "' data-airline-id='" + entry.airlineId + "'></div>")
                $row.append("<div class='cell clickable' onclick='loadRivalDetails(null," + entry.airlineId + "); closeModal($(\"#airlinesByUuidModal\"))'>" + getAirlineLogoImg(entry.airlineId) +  entry.airlineName + "</div>")
                $row.append("<div class='cell'>" + (entry.hqAirport ? getAirportText(entry.hqAirport.city, entry.hqAirport.iata) : "-") + "</div>")
                $row.append("<div class='cell'>" + (entry.username || '-') + getUserLevelImg(entry.userLevel) + "</div>")
                $row.append("<div class='cell'>" + entry.userStatus + "</div>")
                $row.append("<div class='cell'>" + modifiersSpan + "</div>")
                $row.append("<div class='cell'>" + entry.lastUpdated + "</div>")
                $row.append("<div class='cell' align='right'>" + entry.occurrence + "</div>")
                 $("#airlinesByUuidModal .airlineByUuidTable").append($row)
            })
            $("#airlinesByUuidModal").fadeIn(500)
        },
        error: function(jqXHR, textStatus, errorThrown) {
                console.log(JSON.stringify(jqXHR));
                console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
        }
    });

}

function banWarning() {
    adminAction("warn", $("#rivalDetails .adminActions").data("userId"))
}

function ban() {
    adminAction("ban", $("#rivalDetails .adminActions").data("userId"))
}
function banAndReset() {
    adminAction("ban-reset", $("#rivalDetails .adminActions").data("userId"))
}
function nerf() {
    adminAction("nerf", $("#rivalDetails .adminActions").data("userId"))
}
function restore() {
    adminAction("restore", $("#rivalDetails .adminActions").data("userId"))
}
function banChat() {
    adminAction("ban-chat", $("#rivalDetails .adminActions").data("userId"))
}
function setBannerWinner() {
    adminActionWithData("set-banner-winner", $("#rivalDetails .adminActions").data("userId"),
    {
        "strength" : parseInt($("#rivalDetails .bannerLoyaltyBonus").val()),
        "airlineId" : parseInt($("#rivalDetails .adminActions").data("airlineId")) //airline specific
    })
}

function setUserLevel() {
    adminActionWithData("set-user-level", $("#rivalDetails .adminActions").data("userId"),
    {
        "level" : parseInt($("#rivalDetails .setUserLevel").val()),
    })
}

function adminSetUsers(action, $modal) {
    var targetUserIds = []
    $.each($modal.find('input:checked'), function(index, input) {
        targetUserIds.push($(input).data('userId'))
    })

    adminMultiAction(action, targetUserIds, function() {
        closeModal($modal)
    })
}

function invalidateCustomization() {
    var airlineId = $("#rivalDetails .adminActions").data("airlineId")
    var url = "/admin/invalidate-customization/" + airlineId
    $.ajax({
        type: 'GET',
        url: url,
        contentType: 'application/json; charset=utf-8',
        dataType: 'json',
        success: function(result) {
            showRivalsCanvas(selectedAirlineId)
        },
        error: function(jqXHR, textStatus, errorThrown) {
                console.log(JSON.stringify(jqXHR));
                console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
        }
    });
}

function switchUser() {
    adminAction("switch", $("#rivalDetails .adminActions").data("userId"), function() { loadUser(false)})
}

function promptAirlineMessage() {
    var selectedAirlineId =  $("#rivalDetails .adminActions").data("airlineId")
    var airline = loadedRivalsById[selectedAirlineId]
    $('#sendAirlineMessageModal .airlineName').text(airline.name)
    $('#sendAirlineMessageModal .sendMessage').val('')
    $('#sendAirlineMessageModal').fadeIn(500)
}

function sendAirlineMessage() {
    var selectedAirlineId = $("#rivalDetails .adminActions").data("airlineId")
    var url = "/admin/send-airline-message/" + selectedAirlineId

    var data = { "message" : $('#sendAirlineMessageModal .sendMessage').val() }
    $.ajax({
        type: 'PUT',
        url: url,
        data: JSON.stringify(data),
        contentType: 'application/json; charset=utf-8',
        dataType: 'json',
        success: function(result) {
            closeModal($('#sendAirlineMessageModal'))
        },
        error: function(jqXHR, textStatus, errorThrown) {
                console.log(JSON.stringify(jqXHR));
                console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
        }
    });
}

function promptBroadcastMessage() {
    $('#sendBroadcastMessageModal .sendMessage').val('')
    $('#sendBroadcastMessageModal').fadeIn(500)
}

function sendBroadcastMessage() {
    var url = "/admin/send-broadcast-message"
    var data = { "message" : $('#sendBroadcastMessageModal .sendMessage').val() }
    	$.ajax({
    		type: 'PUT',
    		url: url,
    	    data: JSON.stringify(data),
    	    contentType: 'application/json; charset=utf-8',
    	    dataType: 'json',
    	    success: function(result) {
                closeModal($('#sendBroadcastMessageModal'))
    	    },
            error: function(jqXHR, textStatus, errorThrown) {
    	            console.log(JSON.stringify(jqXHR));
    	            console.log("AJAX error: " + textStatus + ' : ' + errorThrown);
    	    }
    	});
}

/*
 * Run the next week now.
 *
 * Waiting out a cycle is the right pace for a server people check on daily and
 * the wrong one for an evening with friends, where the interesting part is
 * what happens next. This only asks - the simulation is a separate process and
 * decides for itself - so the button says so and then leaves the page to find
 * out the usual way, when the cycle finishes and the game announces it.
 */
function forceCycle() {
    var $button = $('.forceCycleButton')
    if ($button.data('asked')) {
        return
    }
    $button.data('asked', true)
    var previous = $button.text()
    $button.text('Asked...')

    $.ajax({
        type: 'PUT',
        url: "/admin/force-cycle",
        contentType: 'application/json; charset=utf-8',
        dataType: 'json',
        success: function() {
            // A cycle takes the best part of a minute, and asking twice in
            // that time would only queue up a second week nobody wanted.
            setTimeout(function() {
                $button.data('asked', false)
                $button.text(previous)
            }, 60000)
        },
        error: function(jqXHR) {
            $button.data('asked', false)
            $button.text(previous)
            console.log("Could not force a cycle: " + JSON.stringify(jqXHR))
        }
    });
}

/*
 * Take a backup now.
 *
 * The server starts it and answers immediately - a dump of a played-in world
 * takes longer than a browser will wait, and a timeout on a backup that in
 * fact worked is worse than no button at all. It is off unless
 * AIRLINE_BACKUP_SCRIPT is set, and the server says so rather than failing
 * quietly.
 */
function backupNow() {
    var $button = $('.backupNowButton')
    var previous = $button.text()
    $button.text('Starting...')

    $.ajax({
        type: 'PUT',
        url: "/admin/backup",
        contentType: 'application/json; charset=utf-8',
        dataType: 'json',
        success: function() {
            $button.text('Backup started')
            setTimeout(function() { $button.text(previous) }, 8000)
        },
        error: function(jqXHR) {
            var message = 'Backup failed'
            try {
                message = JSON.parse(jqXHR.responseText).message || message
            } catch (e) { }
            $button.text(previous)
            console.log(message)
            alert(message)
        }
    });
}
