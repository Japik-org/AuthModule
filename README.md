#AuthModule

`Objective:`
authorize users using database

`Require modules:`
ReceiverBuffered, PacketPool, Sender, Authorizable (optional)

`Require services:`
UsersDatabase, Authorizable (optional)

`Settings:`
- _**authorizable-service-names**_ - names of the service where authorization is required
- _**authorizable-module-names**_ - names of the module where authorization is required
- _**packetpool-module-name**_
- _**sender-module-name**_
- _**receiver-module-name**_
- _**usersDb-service-name**_ - name of the database service
- _**authorizable-receiver-ip**_ - indicates to user the ip
- _**authorizable-receiver-port**_ - indicates to user the port
- _**multiconnection-disabled**_ - prohibits having multiple connections for same user
- _**max-connections**_ - max count of authorized users at same time
- _**max-processes**_ - max count of authorizations at same time (parallel threads)

`ModuleConnection (Auth):`
- authorize
- rejectByConnId
- rejectAllByUserId
- rejectAll
- getConnection

`ModuleConnection (Authorizable):`
- authorize
- isAuthorized
- reject
