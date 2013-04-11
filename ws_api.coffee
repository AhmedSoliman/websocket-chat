# Messages from client to server
# Join Room  >> notifyJoin, roomMembers
joinMsg =
  kind: "join"
  id: "1fca2343453985"
  object:
    room: "cloud9ers"


# Send Message  >> message
sendMessageMsg =
  kind: "sendMessage"
  id: "1fca2343453985"
  object:
    room: "cloud9ers"
    body: "hi"


# Create New Room
createRoomMsg =
  kind: "createRoom"
  id: "1fca2343453986"
  object:
    roomname: "cloud9ers"
    ispublic: true # ??


# Messages from server to client

# Notify New Room
newRoomMsg =
  kind: "newRoom"
  object:
    roomname: "cloud9ers"
    username: "Ahmed"


# Notify Join Room
notifyJoinMsg =
  kind: "notifyJoin"
  object:
    room: "cloud9ers"
    user:
      username: "mohamed"
      email: "mohamed@gmail.com"


# Notify Leave Room
notifyLeaveMsg =
  kind: "notifyLeave"
  object:
    room: "cloud9ers"
    user:
      username: "mohamed"
      email: "mohamed@gmail.com"


# Notify Message
messageMsg =
  kind: "message"
  object:
    username: "Ihab"
    room: "cloud9ers"
    body: "hi all"


# Get Room List
# .. request
getRoomListMsg = kind: "getRoomList"

# .. response
roomListMsg =
  kind: "roomList"
  object:
    roomList: ["cloud9ers", "c9"]


# Get Room Members
# .. request
getRoomMembersMsg =
  kind: "getRoomMembers"
  object:
    room: "cloud9ers"


# .. response
roomMembersMsg =
  knid: "roomMembers"
  object:
    roomname: "cloud9ers"
    roomList: [
      usename: "keladawy"
      email: "keladawy@gmail.com"
    ]
