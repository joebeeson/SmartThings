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

/**
 * For a visual of the functionality please visit https://goo.gl/hriAb1
 */
preferences {
	page(
	    name:    "getDeviceDiscoveryPage",
	    title:   "Discovering receivers...",
	    content: "getDeviceDiscoveryPage"
	)
}

/**
 * Dynamic content page to provide the user with a list of verified devices they
 * can install.
 */
def getDeviceDiscoveryPage() {

    // Build a list of verified devices for the user to choose from. This is not
    // really useful on the first go-around but subsequent refreshes should have
    // data provided we found something.
    Map verifiedDevicesOptions = getVerifiedDevices().collectEntries {
        [ it.value.mac, it.value.name ]
    }

    // Start listening for SSDP events, trigger an SSDP discovery and verify any
    // devices that responded.
	doSsdpSubscribe()
	doSendSsdpDiscover()
	doVerifyDevices()

	return dynamicPage(
    	name:            "getDeviceDiscoveryPage",
        title:           "Discovering receivers...",
        nextPage:        "",

        // Not yet sure why this happens
        refreshInterval: 10,
        install:         true,
        uninstall:       true
    ) {
		section(
        	"Please wait while we discover your receivers. This may take a" +
        	"bit. Select your receivers once they appear below and press 'Done'"
        ) {
			input     "selectedDevices", "enum",
            required: true,
            title:    "Receivers (${verifiedDevicesOptions.size() ?: 0} found)",
            multiple: true,
            metadata: [values: verifiedDevicesOptions]
		}
	}
}

/**
 * Triggered upon installation.
 *
 * @see initialize
 */
def installed() {
	log.debug "[installed] Installed with settings: ${settings}"
	initialize()
}

/**
 * Triggered upon update.
 *
 * @see initialize
 */
def updated() {
	log.debug "[updated] Updated with settings: ${settings}"
	initialize()
}

/**
 * Triggered to perform initialization tasks.
 */
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

/**
 * Sends out a LAN discovery message for the SSDP that DirecTV implements. Any
 * responses are funneled through our subscription to our `onSsdpEvent` method.
 *
 * This is done periodically  to check if any  of our devices have changed their
 * IP and subsequently update our child devices with their new address.
 *
 * @see doSsdpSubscribe
 * @see onSsdpEvent
 */
void doSendSsdpDiscover() {
	log.debug "[doSendSsdpDiscover] Sending 'lan discovery " +
              "urn:schemas-upnp-org:device:MediaServer:1' command..."
	sendHubCommand(
    	new physicalgraph.device.HubAction(
        	"lan discovery urn:schemas-upnp-org:device:MediaServer:1",
            physicalgraph.device.Protocol.LAN
        )
    )
}

/**
 * Creates a subscription for our SSDP term and connects `onSsdpEvent` to handle
 * responses.  The `doSendSsdpDiscover` method handles triggering  the discovery
 * event to the network.
 *
 * @see doSendSsdpDiscover
 * @see onSsdpEvent
 */
void doSsdpSubscribe() {
	log.debug "[doSsdpSubscribe] Subscribing to " +
	          "'ssdpTerm.urn:schemas-upnp-org:device:MediaServer:1' events"
	subscribe(
    	location,
        "ssdpTerm.urn:schemas-upnp-org:device:MediaServer:1",
        onSsdpEvent
    )
}

/**
 * We handle attempting to verify devices which have responded to our SSDP event
 * by requesting their description; basically metadata for the device.
 *
 * To accomplish this  we send  an HTTP request for each unverified device which
 * is handled in our `onVerifyDeviceDescription` method where it inspects if the
 * description matches our expectations of a DirecTV receiver and then flags the
 * device as verified if it does.
 *
 * @see onVerifyDeviceDescription
 */
void doVerifyDevices() {
	log.debug "[doVerifyDevices] Verifying unverified devices..."
	getUnverifiedDevices().each {
		int devicePort = Integer.parseInt(it.value.deviceAddress, 16)
		String deviceIp = it.value.networkAddress.toList().collate(2).collect {
		    Integer.parseInt(it.join(), 16)
		}.join(".")
		String deviceAddress = "${deviceIp}:${devicePort}"
        log.trace "[doVerifyDevices] Found unverified device at " +
                  "'${deviceAddress}', sending description request..."
		sendHubCommand(
        	new physicalgraph.device.HubAction(
            	"""GET ${it.value.ssdpPath} HTTP/1.1\r\nHOST: ${deviceAddress}\r\n\r\n""",
                physicalgraph.device.Protocol.LAN,
                deviceAddressStr,
                [callback: onVerifyDeviceDescription]
            )
        )
	}
}

/**
 * Convenience method for retrieving a list of devices from our persistent state
 * while handling the situation where there was no prior state.
 */
def getDevices() {
	if (!state.devices) {
	    log.trace "[getDevices] Populating 'state.devices' with an empty map"
		state.devices = [:]
	}
	state.devices
}

/**
 * Convenience method for retrieving a list of devices that are not yet verified
 *
 * @see getDevices
 * @see onVerifyDeviceDescription
 */
def getUnverifiedDevices() {
	getDevices().findAll{ it.value.verified != true }
}

/**
 * Convenience method for retrieving a list of devices that have been verified.
 *
 * @see getDevices
 */
def getVerifiedDevices() {
	getDevices().findAll{ it.value.verified == true }
}

/**
 * Loop over  the user's selections from  the "getDeviceDiscoveryPage" page  and
 * add each that don't yet exist.
 *
 * @see getDeviceDiscoveryPage
 */
def addDevices() {
    log.debug "[addDevices] Adding selected devices..."
	selectedDevices.each { deviceNetworkId ->
		def selectedDevice = getDevices().find {
		    it.value.mac == deviceNetworkId
		}
		def childDevice
		if (selectedDevice) {
			childDevice = getChildDevices()?.find {
				it.deviceNetworkId == selectedDevice.value.mac
			}
		}

		if (!childDevice) {
	        log.info "[addDevices] Creating new device!"
			addChildDevice(
            	"joebeeson",
                "DirecTV Receiver",
                selectedDevice.value.mac,
                selectedDevice?.value.hub,
                [
					"label": selectedDevice?.value?.name,
					"data": [
						"ip": selectedDevice.value.networkAddress
					]
				]
            )
		}
	}
}

/**
 * Subscription event handler  for  our "ssdpTerm" subscription created  in  our
 * `doSsdpSubscribe` method.
 *
 * If the device is unknown to us (it doesn't exist  in our `state.devices` map)
 * then we'll add it.
 *
 * If the device  is known we'll check  if any  of  its values have been updated
 * since the last time we've seen it.  Furthermore if the device is  a child  of
 * ours we'll call the `sync` method on it to (potentially) update its IP.
 *
 * @see doSsdpSubscribe
 * @see (DirecTV Receiver).sync
 */
void onSsdpEvent(evt) {
	def devices = getDevices()
	def parsedEvent = parseLanMessage(evt.description)
	parsedEvent << ["hub": evt?.hubId]
	log.debug "[onSsdpEvent] Received SSDP event: ${parsedEvent}"

	String ssdpUSN = parsedEvent.ssdpUSN.toString()
	if (devices."${ssdpUSN}") {
		def device = devices."${ssdpUSN}"
		if (
		    (device.networkAddress != parsedEvent.networkAddress) ||
		    (device.deviceAddress != parsedEvent.deviceAddress)
		) {
            log.trace "[onSsdpEvent] Device values differ from state. Updating."
			device.networkAddress = parsedEvent.networkAddress
			device.deviceAddress = parsedEvent.deviceAddress
			def child = getChildDevice(parsedEvent.mac)
			if (child) {

	            // Let  our child device know  that  its address  has (probably)
	            // been changed.  It's  up  to  the device  to determine how  to
	            // handle this situation.
			    log.trace "[onSsdpEvent] Found child device, updating via " +
			              "values via 'sync' method..."
				child.sync(parsedEvent.networkAddress)
			}
		} else {
            log.trace "[onSsdpEvent] Device already exists and values match." +
                      "Ignoring."
		}
	} else {

        // We've never seen this device before. Add it to our map of devices for
        // later inspection/verification.
	    log.trace "[onSsdpEvent] New device, adding to state..."
		devices << ["${ssdpUSN}": parsedEvent]
	}
}

/**
 * Callback for our `doVerifyDevices` method.  We're sent  the response for  the
 * device's description XML which we then inspect to ensure  it appears to be  a
 * DirecTV receiver before updating the corresponding `state.devices` entry with
 * a "verified" value.
 *
 * @see doVerifyDevices
 */
void onVerifyDeviceDescription(physicalgraph.device.HubResponse hubResponse) {
	log.debug "[onVerifyDeviceDescription] Received response, checking..."
	def device = getDevices().find {
	    it?.key?.contains(body?.device?.UDN?.text())
	}
	if (device) {
        def body = hubResponse.xml
    	if (body?.device?.manufacturer?.text() == "DIRECTV") {
        	log.trace "[onVerifyDeviceDescription] Device appears to be a " +
        	          "DirecTV receiver, verifying!"

        	// The device's description appears to be a DirecTV receiver so lets
        	// update the device with the "verified" flag.
			device.value << [
			    name: body?.device?.modelDescription?.text(),
			    verified: true
			]
        }
	} else {

        // This is one  of those "this should never happen" situations but let's
        // go ahead and log the event anyways.  Basically  if  we get here we've
        // somehow been triggered for a request on a device we don't know about.
	    log.warn "[onVerifyDeviceDescription] Huh. Not sure how we received a" +
	             "response for something we don't have in our `state.devices`"
	}
}