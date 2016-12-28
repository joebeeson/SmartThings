/**
 * DirecTV Receiver
 *
 * Created by Joe Beeson <jbeeson@gmail.com>
 *
 * This is free and unencumbered software released into the public domain.
 *
 * Anyone is free to copy, modify, publish, use, compile, sell, or
 * distribute this software, either in source code form or as a compiled
 * binary, for any purpose, commercial or non-commercial, and by any
 * means.
 *
 * In jurisdictions that recognize copyright laws, the author or authors
 * of this software dedicate any and all copyright interest in the
 * software to the public domain. We make this dedication for the benefit
 * of the public at large and to the detriment of our heirs and
 * successors. We intend this dedication to be an overt act of
 * relinquishment in perpetuity of all present and future rights to this
 * software under copyright law.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 *
 * For more information, please refer to <http://unlicense.org/>
 */
metadata {
	definition (name: "DirecTV Receiver", namespace: "joebeeson", author: "Joe Beeson") {
		capability "Actuator"
        capability "Polling"
        capability "Refresh"
		capability "Sensor"
		capability "Switch"
		capability "TV"
        
        attribute "channelCallSign", "string"
        attribute "programTitle", "string"
        attribute "programRating", "string"
        
        command "button0"
        command "button1"
        command "button2"
        command "button3"
        command "button4"
        command "button5"
        command "button6"
        command "button7"
        command "button8"
        command "button9"
        command "play"
        command "pause"
	}
    
    preferences {
    	input name: "receiverIpStr", type: "text", title: "IP Address", description: "Receiver IP", required: true, displayDuringSetup: true
        input name: "receiverPortInt", type: "number", title: "Port", description: "Receiver port", range: "1..65535", required: true, displayDuringSetup: true
        input name: "receiverMacStr", type: "text", title: "MAC Address", description: "Receiver MAC", required: false, displayDuringSetup: true
    }

	simulator {}

	tiles(scale: 2) {
        standardTile("switch", "device.switch", width: 6, height: 4, canChangeIcon: true) {
            state "off", label: '${currentValue}', action: "switch.on", icon: "st.Electronics.electronics18", backgroundColor: "#ffffff"
            state "on", label: '${currentValue}', action: "switch.off", icon: "st.Electronics.electronics18", backgroundColor: "#79b821"
        }
        
        standardTile("refresh", "device.switch", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "default", label: "", action: "refresh.refresh", icon: "st.secondary.refresh"
		}    
        
        standardTile("play", "device.play", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "default", label: "", action: "play", icon: "st.sonos.play-btn"
		}  
        
        standardTile("pause", "device.pause", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "default", label: "", action: "pause", icon: "st.sonos.pause-btn"
		}  
        
        standardTile("button0", "device.button0", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "default", label: "0", action: "button0"
		}
        
        standardTile("button1", "device.button1", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "default", label: "1", action: "button1"
		}  
        
        standardTile("button2", "device.button1", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "default", label: "2", action: "button2"
		}  
        
        standardTile("button3", "device.button3", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "default", label: "3", action: "button3"
		}  
        
        standardTile("button4", "device.button4", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "default", label: "4", action: "button4"
		}  
        
        standardTile("button5", "device.button5", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "default", label: "5", action: "button5"
		}  
        
        standardTile("button6", "device.button6", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "default", label: "6", action: "button6"
		}  
        
        standardTile("button7", "device.button7", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "default", label: "7", action: "button7"
		}  
        
        standardTile("button8", "device.button8", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "default", label: "8", action: "button8"
		}  
        
        standardTile("button9", "device.button9", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "default", label: "9", action: "button9"
		}  
        
        standardTile("buttonDash", "device.buttonDash", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "default", label: "-", action: "buttonDash"
		}  
        
        standardTile("buttonEnter", "device.buttonEnter", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "default", label: "⏎", action: "buttonEnter"
		}  

        main("switch")
        details(
        	[
            	"switch", 
                
                "refresh", 
                "play", 
                "pause",
                
                "button1",
                "button2",
                "button3",
                
                "button4",
                "button5",
                "button6",
                
                "button7",
                "button8",
                "button9",
                
                "buttonDash",
                "button0",
                "buttonEnter"
            ]
        )
	}
}

def parse(String description) {
    def responseJsonMap = parseLanMessage(description).json
    log.trace "[parse] responseJsonMap = ${responseJsonMap}"
    def responseStatusCodeInt = responseJsonMap.status.code
    def responseStatusQueryStr = responseJsonMap.status.query
	switch (responseStatusQueryStr) {
       	case "/info/mode":
            return createEvent(name: "switch", value: responseJsonMap.mode == 1 ? "off" : "on")
        case "/tv/getTuned":
        	return [
            	createEvent(name: "channel", value: responseJsonMap.major),
                createEvent(name: "channelCallSign", value: responseJsonMap.callsign),
                createEvent(name: "programTitle", value: responseJsonMap.title),
                createEvent(name: "programRating", value: responseJsonMap.rating)
            ]
        default:
        	if (responseStatusQueryStr.startsWith("/remote/processKey")) {
            	if (responseStatusCodeInt == 200) {
                    switch (responseJsonMap.key) {
                        case "poweron":
							return createEvent(name: "switch", value: "on")
                        case "poweroff":
                        	return createEvent(name: "switch", value: "off")
                    }
                }
            } else {
            	log.debug "Unhandled 'responseJsonMap.status.query': ${responseJsonMap.status.query}"
            	log.debug "Body of unhandled is: ${responseJsonMap}"
            }
    }
}

def button0() {
	log.info "[button0] Executing 'button0'"
    sendKey("0")
}

def button1() {
	log.info "[button1] Executing 'button1'"
    sendKey("1")
}

def button2() {
	log.info "[button2] Executing 'button2'"
    sendKey("2")
}

def button3() {
	log.info "[button3] Executing 'button3'"
    sendKey("3")
}

def button4() {
	log.info "[button4] Executing 'button4'"
    sendKey("4")
}

def button5() {
	log.info "[button5] Executing 'button5'"
    sendKey("5")
}

def button6() {
	log.info "[button6] Executing 'button6'"
    sendKey("6")
}

def button7() {
	log.info "[button7] Executing 'button7'"
    sendKey("7")
}

def button8() {
	log.info "[button8] Executing 'button8'"
    sendKey("8")
}

def button9() {
	log.info "[button9] Executing 'button9'"
    sendKey("9")
}

def buttonDash() {
	log.info "[buttonDash] Executing 'buttonDash'"
    sendKey("dash")
}

def buttonEnter() {
	log.info "[buttonEnter] Executing 'buttonEnter'"
    sendKey("enter")
}

def channelDown() {
	log.info "[channelDown] Executing 'channelDown'"
    sendKey("chandown")
}

def channelUp() {
	log.info "[channelUp] Executing 'channelUp'"
    sendKey("chanup")
}

def initialize() {
	log.debug "[initialize] Initializing!"
    def receiverIpHexStr = receiverIpStr.tokenize( '.' ).collect { String.format('%02x', it.toInteger()) }.join()
    def receiverPortHexStr = receiverPortInt.toString().format('%04x', receiverPortInt.toInteger())
    log.trace "[initialize] Setting 'deviceNetworkId' to '${receiverIpHexStr}:${receiverPortHexStr}'"
    device.setDeviceNetworkId("${receiverIpHexStr}:${receiverPortHexStr}")
	schedule("0 0/1 * 1/1 * ? *", poll)
}

def installed() {
	log.debug "[installed] Installed!"
    initialize()
}

def off() {
	log.info "[off] Executing 'off'"
	sendKey("poweroff")
}

def on() {
	log.info "[on] Executing 'on'"
	sendKey("poweron")
}

def play() {
	log.info "[play] Executing 'play'"
    sendKey("play")
}

def poll() {
	log.info "[poll] Executing 'poll'"
    doReceiverRequest("/info/mode")
    doReceiverRequest("/tv/getTuned")
}

def pause() {
	log.info "[pause] Executing 'pause'"
    sendKey("pause")
}

def refresh() {
	log.info "[refresh] Executing 'refresh'"
    poll()
}

/**
 * Convenience method for sending a key, by name, to the receiver. Triggers the
 * `doReceiverRequest` method to perform the actual communication.
 *
 * @param	keyNameStr			Name of the key to send. 
 * @see 	doReceiverRequest
 */
def sendKey(keyNameStr) {
	log.debug "[sendKey] Sending key '${keyNameStr}'"
    return doReceiverRequest("/remote/processKey", [hold: "keyPress", key : keyNameStr])
}

def updated() {
	log.debug "[updated] Updated!"
    initialize()
}

def volumeDown() {}

def volumeUp() {}

/**
 * Sends a request to the receiver. The response will be sent to our `parse` method where
 * it will handle updating our state based on the response.
 *
 * @param	requestUrlStr		The path, without query parameters, to send. Required.
 * @param	requestQueryMap		Map of query parameters to send, defaults to nothing.
 * @param	requestMethodStr	Name of the request method (GET, PUT, POST, etc.), default
 *								is "GET" -- which is pretty much the only thing that the
 *								receiver actually supports.
 */
private doReceiverRequest(requestUrlStr, requestQueryMap = [], requestMethodStr = "GET") {
	log.debug "[doReceiverRequest] Requesting '${requestUrlStr}' with query " +
			  "'${requestQueryMap}' as '${requestMethodStr}' to " + 
			  "'${receiverIpStr}:${receiverPortInt}'"
	sendHubCommand(
		new physicalgraph.device.HubAction(
			headers: [
				HOST: "${receiverIpStr}:${receiverPortInt}"
			],
			method: requestMethodStr,
			path: requestUrlStr,
			query: requestQueryMap + [
				clientAddr: (receiverMacStr ?: "0").replace(":", "").toUpperCase()
			]
		)
	)
}
