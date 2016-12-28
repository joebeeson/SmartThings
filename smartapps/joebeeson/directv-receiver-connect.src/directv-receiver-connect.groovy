definition(
    name:        "DirecTV Receiver (Connect)",
    namespace:   "joebeeson",
    author:      "Joe Beeson",
    description: "DirecTV Receiver (Connect)",
    category:    "Convenience",
    iconUrl:     "http://a4.mzstatic.com/us/r30/Purple18/v4/41/93/a8/4193a8ee-0863-0458-7d03-fd050517c400/icon175x175.jpeg",
    iconX2Url:   "http://a4.mzstatic.com/us/r30/Purple18/v4/41/93/a8/4193a8ee-0863-0458-7d03-fd050517c400/icon175x175.jpeg",
    iconX3Url:   "http://a4.mzstatic.com/us/r30/Purple18/v4/41/93/a8/4193a8ee-0863-0458-7d03-fd050517c400/icon175x175.jpeg"
)

preferences {
	page(name: "deviceDiscoveryPage", title: "Discovering receivers...", content: "deviceDiscoveryPage")
}

def deviceDiscoveryPage() {
	def options = [:]
	def devices = getVerifiedDevices()
	devices.each {
		def value = it.value.name ?: "UPnP Device ${it.value.ssdpUSN.split(':')[1][-3..-1]}"
		def key = it.value.mac
		options["${key}"] = value
	}
    
	doSsdpSubscribe()
	doSendSsdpDiscover()
	verifyDevices()

	return dynamicPage(
    	name:            "deviceDiscoveryPage", 
        title:           "Discovering receivers...", 
        nextPage:        "", 
        refreshInterval: 5, 
        install:         true, 
        uninstall:       true
    ) {
		section(
        	"Please wait while we discover your receivers. Select your receivers once they appear below."
        ) {
			input     "selectedDevices", "enum", 
            required: true, 
            title:    "Recivers (${options.size() ?: 0} found)", 
            multiple: true, 
            metadata: [values: options]
		}
	}
}

def installed() {
	log.debug "[installed] Installed with settings: ${settings}"
	initialize()
}

def updated() {
	log.debug "[updated] Updated with settings: ${settings}"
	unsubscribe()
	initialize()
}

def initialize() {
	log.debug "[initialize] Initializing application..."
	unsubscribe()
	unschedule()
	doSsdpSubscribe()

	if (selectedDevices) {
		addDevices()
	}

	runEvery5Minutes("doSendSsdpDiscover")
}

void doSendSsdpDiscover() {
	log.debug "[doSendSsdpDiscover] Sending 'lan discovery urn:schemas-upnp-org:device:MediaServer:1' command..."
	sendHubCommand(
    	new physicalgraph.device.HubAction(
        	"lan discovery urn:schemas-upnp-org:device:MediaServer:1", 
            physicalgraph.device.Protocol.LAN
        )
    )
}

void doSsdpSubscribe() {
	log.debug "[doSsdpSubscribe] Subscribing to 'ssdpTerm.urn:schemas-upnp-org:device:MediaServer:1' events"
	subscribe(
    	location, 
        "ssdpTerm.urn:schemas-upnp-org:device:MediaServer:1", 
        onSsdpEvent
    )
}

Map verifiedDevices() {
	def devices = getVerifiedDevices()
	def map = [:]
	devices.each {
		def value = it.value.name ?: "UPnP Device ${it.value.ssdpUSN.split(':')[1][-3..-1]}"
		def key = it.value.mac
		map["${key}"] = value
	}
	map
}

void verifyDevices() {
	log.debug "[verifyDevices] Verifying devices..."
	getDevices().each {
		int port = convertHexToInt(it.value.deviceAddress)
		String ip = convertHexToIP(it.value.networkAddress)
		String host = "${ip}:${port}"
        log.trace "[verifyDevices] Found unverified device at '${host}', sending description request..."
		sendHubCommand(
        	new physicalgraph.device.HubAction(
            	"""GET ${it.value.ssdpPath} HTTP/1.1\r\nHOST: $host\r\n\r\n""", 
                physicalgraph.device.Protocol.LAN, 
                host, 
                [callback: onDeviceDescriptionEvent]
            )
        )
	}
}

def getUnverifiedDevices() {
	getDevices().findAll{ it.value.verified != true }
}

def getVerifiedDevices() {
	getDevices().findAll{ it.value.verified == true }
}

def getDevices() {
	if (!state.devices) {
		state.devices = [:]
	}
	state.devices
}

def addDevices() {
	selectedDevices.each { dni ->
		def selectedDevice = getDevices().find { it.value.mac == dni }
		def d
		if (selectedDevice) {
			d = getChildDevices()?.find {
				it.deviceNetworkId == selectedDevice.value.mac
			}
		}

		if (!d) {
			addChildDevice(
            	"joebeeson", 
                "DirecTV Receiver", 
                selectedDevice.value.mac, 
                selectedDevice?.value.hub, 
                [
					"label": selectedDevice?.value?.name ?: "Generic UPnP Device",
					"data": [
						"mac": selectedDevice.value.mac,
						"ip": selectedDevice.value.networkAddress
					]
				]
            )
		}
	}
}

def onSsdpEvent(evt) {
	def description = evt.description
	def hub = evt?.hubId

	def parsedEvent = parseLanMessage(description)
	parsedEvent << ["hub":hub]

	def devices = getDevices()
	String ssdpUSN = parsedEvent.ssdpUSN.toString()
	if (devices."${ssdpUSN}") {
		def d = devices."${ssdpUSN}"
		if (d.networkAddress != parsedEvent.networkAddress || d.deviceAddress != parsedEvent.deviceAddress) {
			d.networkAddress = parsedEvent.networkAddress
			d.deviceAddress = parsedEvent.deviceAddress
            log.debug parsedEvent
			def child = getChildDevice(parsedEvent.mac)
			if (child) {
				child.sync(parsedEvent.networkAddress, parsedEvent.deviceAddress)
			}
		}
	} else {
		devices << ["${ssdpUSN}": parsedEvent]
	}
}

void onDeviceDescriptionEvent(physicalgraph.device.HubResponse hubResponse) {
	log.debug "[onDeviceDescriptionEvent] Received response, checking..."
	def body = hubResponse.xml
	def devices = getDevices()
	def device = devices.find { it?.key?.contains(body?.device?.UDN?.text()) }
	if (device) {
    	if (body?.device?.manufacturer?.text() == "DIRECTV") {
        	log.trace "[onDeviceDescriptionEvent] Device appears to be a DirecTV receiver!"
			device.value << [name: body?.device?.modelDescription?.text(), model:body?.device?.modelName?.text(), serialNumber:body?.device?.serialNum?.text(), verified: true]
        }
	}
}

private Integer convertHexToInt(hex) {
	Integer.parseInt(hex,16)
}

private String convertHexToIP(hex) {
	[convertHexToInt(hex[0..1]),convertHexToInt(hex[2..3]),convertHexToInt(hex[4..5]),convertHexToInt(hex[6..7])].join(".")
}