WS = if window['MozWebSocket']
		MozWebSocket
	else
		WebSocket
user = "user" + Math.random().toString().substring(2,6)
email = "#{user}@gmail.com"
room = "room1"
chatSocket = new WS("ws://localhost:9000/room/ws?room=#{room}&amp;username=#{user}&amp;email=#{email}")
connected = false
chatSocket.onopen = () -> connected = true
chatSocket.onclose = () -> connected = false
chatSocket.onmessage = (message) ->
	receiveEvent(JSON.parse(message.data))

sendMessage = () ->
	$talk = $("#talk")
	body = $talk.val()
	roomname = $("#change-room").val()
	if connected
		sendMessageMsg =
		  kind: "sendMessage"
		  id: "1fca2343453985"  #TODO: should be random
		  object:
		    room: room
		    body: body
		chatSocket.send(JSON.stringify(sendMessageMsg))
		$talk.val("")

joinRoom = ($li) ->
	roomname = $li.text()
	joinMsg =
	  kind: "join"
	  id: "1fca2343453985"  #TODO: should be random
	  object:
	  	room: roomname
	chatSocket.send(JSON.stringify(joinMsg))
	room = roomname
	$(".room-name").text(roomname)

createRoom = () ->
	$createRoomTxt = $("#create-room")
	roomname = $createRoomTxt.val()
	createRoomMsg =
	  kind: "createRoom"
	  id: "1fca2343453986"
	  object:
	    roomname: roomname
	    ispublic: true
	chatSocket.send(JSON.stringify(createRoomMsg))
	$createRoomTxt.val("")
	appendRoom(roomname)

appendRoom = (roomname) ->
	if $("#rooms").find("li").find("a[href='#{roomname}']").length == 0
		$("#rooms").append("<li><a href='#{roomname}'>#{roomname}</a></li>")

appendMember = (user) ->
	if $("#members").find("li[data-username='#{user.username}']").length == 0
		$("#members").append("<li data-email='#{user.email}', data-username=#{user.username}>" + user.username + '</li>')

getRoomList = () ->
	getRoomListMsg = kind: "getRoomList"
	chatSocket.send(JSON.stringify(getRoomListMsg))

getRoomMembers = () ->
	roomname = $(".room-name").val()
	getRoomMembersMsg =
	  kind: "getRoomMembers"
	  object:
	    room: roomname
	chatSocket.send(JSON.stringify(getRoomMembersMsg))

receiveEvent = (data) ->
	notify = (username, body) ->
		# Create the message element
		# Create the message element
		el = $('<div class="message"><span></span><p></p></div>')
		$("span", el).text(username)
		$("p", el).text(body)
		$(el).addClass(data.kind)
		if username == "#{user}"
			(el).addClass('me')
		$('#messages').append(el)

	# Handle errors
	if data.error
		chatSocket.close()
		$("onError span").text(data.error)
		$("#onError").show()
	else
		$("#onChat").show()

	switch data.kind
		when "notifyJoin"
			console.log "notifyJoin: " + JSON.stringify(data)
			notify(data.object.user.username, "has joind the room: #{data.object.room}")

		when "message"
			console.log "message: " + JSON.stringify(data)
			notify(data.object.username, "#{data.object.body} (#{data.object.room})")

		when "roomList"
			console.log "roomList: " + JSON.stringify(data)
			$.each(data.object.roomList, (_, roomname) -> appendRoom(roomname))

		when "roomMembers"
			console.log "roomMembers: " + JSON.stringify(data)
			$.each(data.object.membersList, (_, user) -> appendMember(user))

		when "notifyLeave"
			console.log "notifyLeave: " + JSON.stringify(data)
			notify(data.object.username, "has left room: #{data.object.room}")


handleReturnKey = (element, f) ->
	element.keypress((e) ->
		if e.charCode == 13 || e.keyCode == 13
			f(e)
		)

# Send a message
handleReturnKey($("#talk"), (e) ->
		if connected
			e.preventDefault()
			sendMessage()
	)

# Create a room
handleReturnKey($("#create-room"), (e) ->
		if connected
			e.preventDefault()
			createRoom()
	)

# JoinRoom
$("#rooms").on("click", "li", (e) ->
		e.preventDefault()
		joinRoom($(this))
	)

# Get RoomList
$("#get-room-list").on("click", (e) ->
		e.preventDefault()
		getRoomList()
	)

# Get RoomMembers
$("#get-room-members").on("click", (e) ->
		e.preventDefault()
		getRoomMembers()
	)
