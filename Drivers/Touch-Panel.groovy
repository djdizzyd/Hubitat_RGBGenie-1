/*
*	Touch Panel Driver
*	Code written for RGBGenie by Bryan Copeland
*
*
*/
metadata {
	definition (name: "RGBGenie Touch Panel", namespace: "rgbgenie", author: "RGBGenie") {
        capability "Actuator"
        capability "Configuration"
        capability "Refresh"

		fingerprint mfr:"0330", prod:"0301", deviceId:"A109", inClusters:"0x5E,0x85,0x59,0x8E,0x55,0x86,0x72,0x5A,0x73,0x98,0x9F,0x6C,0x5B,0x7A", outClusters:"0x26,0x2B,0x2C", deviceJoinName: "RGBGenie Dimmer Touch Panel"
        fingerprint mfr:"0330", prod:"0301", deviceId:"A106", inClusters:"0x5E,0x85,0x59,0x8E,0x55,0x86,0x72,0x5A,0x73,0x98,0x9F,0x6C,0x5B,0x7A", outClusters:"0x26,0x33,0x2B,0x2C", deviceJoinName: "RGBGenie 3 Scene Color Touch Panel"
        fingerprint mfr:"0330", prod:"0301", deviceId:"A105", inClusters:"0x5E,0x85,0x59,0x8E,0x55,0x86,0x72,0x5A,0x73,0x98,0x9F,0x6C,0x5B,0x7A", outClusters:"0x26,0x33,0x2B,0x2C", deviceJoinName: "RGBGenie 3 Zone Color Touch Panel"
        fingerprint mfr:"0330", prod:"0301", deviceId:"A101", inClusters:"0x5E,0x85,0x59,0x8E,0x55,0x86,0x72,0x5A,0x73,0x98,0x9F,0x6C,0x5B,0x7A", outClusters:"0x26,0x33,0x2B,0x2C", deviceJoinName: "RGBGenie Color Temperature Touch Panel"

    }
    preferences {
        input name: "addHubZone1", type: "bool", description: "", title: "Create child driver for Zone 1", required: true, defaultValue: false
        if (getDataValue("deviceId")!="41222") {
            input name: "addHubZone2", type: "bool", description: "", title: "Create child driver for Zone 2", required: true, defaultValue: false
            input name: "addHubZone3", type: "bool", description: "", title: "Create child driver for Zone 3", required: true, defaultValue: false
        }
        input name: "associationsZ1", type: "string", description: "", title: "Zone 1 Associations", required: true
        if (getDataValue("deviceId")!="41222") {
            input name: "associationsZ2", type: "string", description: "", title: "Zone 2 Associations", required: true
            input name: "associationsZ3", type: "string", description: "", title: "Zone 3 Associations", required: true
        }
		input description: "To add nodes to zone associations use the Hexidecimal nodeID from the z-wave device list separated by commas", title: "Direct Association", displayDuringSetup: false, type: "paragraph", element: "paragraph"
        input name: "logEnable", type: "bool", description: "", title: "Enable Debug Logging", defaultValue: true, required: true
	}
}

private getDRIVER_VER() { "0.001" }
private getCOMMAND_CLASS_VERS() { [] }
private getZONE_MODEL() {
    if (getDataValue("deviceId")!="41222") {
        return true
    } else {
        return false
	}
}
private getNUMBER_OF_GROUPS() { 
    if (ZONE_MODEL) {
        return 4
	} else {
        return 2
	}
}

def initialize() {
    def cmds=[]

    if (getDataValue("driverVer") != "0.001") {
        updateDataValue("zwaveAssociationG1", "")
        updateDataValue("zwaveAssociationG2", "")
        updateDataValue("zwaveAssociationG3", "")
        updateDataValue("zwaveAssociationG4", "")
        //cmds << zwave.associationV2.associationSet(groupingIdentifier: 1, nodeId: zwaveHubNodeId)
	}
    cmds+=pollAssociations()
    updateDataValue("driverVer", DRIVER_VER)
    cmds+=processAssociations()
    commands(cmds)
}

def logsOff() {
	log.warn "debug logging disabled..."
	device.updateSetting("logEnable", [value: "false", type: "bool"])
	if (logEnable) runIn(1800,logsOff)
}

def updated() {
    def cmds=[]
    cmds+=processAssociations()
    cmds+=pollAssociations()
    for (int i = 1 ; i <= 3; i++) {
        if (settings."addHubZone$i") {
            if (!getChildDevice("${device.deviceNetworkId}-$i")) {
                def child=addChildDevice("rgbgenie", "RGBGenie Touch Panel Child", "${device.deviceNetworkId}-$i", [completedSetup: true, label: "${device.displayName} (Zone$i)", isComponent: true, componentName: "zone$i", componentLabel: "Zone $i"])
                if (child) {
                    child.defineMe(getDataValue("deviceId"))           
				}        
            }
            cmds << addHubMultiChannel(i)
        } else {
            if (getChildDevice("${device.deviceNetworkId}-$i")) {
                deleteChildDevice("${device.deviceNetworkId}-$i")
            }
            cmds << removeHubMultiChannel(i)
	    }
    }
    if (logEnable) log.debug "updated cmds: ${cmds}"
   	if (logEnable) runIn(1800,logsOff)
    commands(cmds)
}


def refresh() {
    def cmds=[]
    cmds+=pollAssociations()
    commands(cmds)
}

def installed() {
	device.updateSetting("logEnable", [value: "true", type: "bool"])
	runIn(1800,logsOff)
    initialize()
}



def pollAssociations() {
    def cmds=[]
    for(int i = 1;i<=NUMBER_OF_GROUPS;i++) {
        cmds << zwave.associationV2.associationGet(groupingIdentifier:i)
    }
    if (logEnable) log.debug "pollAssociations cmds: ${cmds}"
    return cmds
}

def configure() {
    initialize()
}

def zwaveEvent(hubitat.zwave.commands.multichannelv3.MultiChannelCmdEncap cmd) {
    def encapsulatedCommand = cmd.encapsulatedCommand()
    if (encapsulatedCommand) {
        def child=getChildDevice("${device.deviceNetworkId}-${cmd.destinationEndPoint}")
        if (child) {
            child.zwaveEvent(encapsulatedCommand)
	    }
    }
}

def zwaveEvent(hubitat.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
    def encapsulatedCommand = cmd.encapsulatedCommand(commandClassVersions)
    if (encapsulatedCommand) {
        state.sec = 1
        zwaveEvent(encapsulatedCommand)
    }
}

def zwaveEvent(hubitat.zwave.Command cmd) {
    if (logEnable) log.debug "skip:${cmd}"
}

def parse(description) {
    def result = null
    if (description != "updated") {
        def cmd = zwave.parse(description, commandClassVersions)
        if (cmd) {
            result = zwaveEvent(cmd)
            //if (logEnable) log.debug("'$cmd' parsed to $result")
        } else {
            if (logEnable) log.debug "Couldn't zwave.parse '$description'" 
        }
    }
    def now
    if(location.timeZone)
    now = new Date().format("yyyy MMM dd EEE h:mm:ss a", location.timeZone)
    else
    now = new Date().format("yyyy MMM dd EEE h:mm:ss a")
    sendEvent(name: "lastActivity", value: now, displayed:false)
    result
}

private command(hubitat.zwave.Command cmd) {
    if (state.sec) {
        zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format()
    } else {
        cmd.format()
    }
}

private commands(commands, delay=200) {
    delayBetween(commands.collect{ command(it) }, delay)
}

def setDefaultAssociation() {
    //def hubitatHubID = (zwaveHubNodeId.toString().format( '%02x', zwaveHubNodeId )).toUpperCase()
    def cmds=[]
    cmds << zwave.associationV2.associationSet(groupingIdentifier: 1, nodeId: zwaveHubNodeId)
    return cmds
}

def addHubMultiChannel(zone) {
    def cmds=[]
    def group=zone+1
    cmds << zwave.multiChannelAssociationV2.multiChannelAssociationSet(groupingIdentifier: group, nodeId: [0,zwaveHubNodeId,zone as Integer])
    return cmds
}

def removeHubMultiChannel(zone) {
    def cmds=[]
    def group=zone+1
    cmds << zwave.multiChannelAssociationV2.multiChannelAssociationRemove(groupingIdentifier: group, nodeId: [0,zwaveHubNodeId,zone as Integer])
    return cmds
}

def processAssociations(){
    def cmds = []
    cmds += setDefaultAssociation()
        def associationGroups = NUMBER_OF_GROUPS
        for (int i = 2 ; i <= associationGroups; i++) {
            def z=i-1
            if (logEnable) log.debug "group: $i dataValue: " + getDataValue("zwaveAssociationG$i") + " parameterValue: " + settings."associationsZ$z"
            def parameterInput=settings."associationsZ$z"
            def newNodeList = []
            def oldNodeList = []
            if (getDataValue("zwaveAssociationG$i") != null) {
                getDataValue("zwaveAssociationG$i").minus("[").minus("]").split(",").each {
                    if (it!="") {
                        oldNodeList.add(it.minus(" "))
                    }
			    }
            }
            if (parameterInput!=null) {
                parameterInput.minus("[").minus("]").split(",").each {
                    if (it!="") {
                        newNodeList.add(it.minus(" "))
                    }
				}
            }
            if (oldNodeList.size > 0 || newNodeList.size > 0) {
                if (logEnable) log.debug "${oldNodeList.size} - ${newNodeList.size}"
                oldNodeList.each {
                    if (!newNodeList.contains(it)) {
                        // user removed a node from the list
                        if (logEnable) log.debug "removing node: $it, from group: $i"
                        cmds << zwave.associationV2.associationRemove(groupingIdentifier:i, nodeId:Integer.parseInt(it,16))
					}        
				}
                newNodeList.each {
                    cmds << zwave.associationV2.associationSet(groupingIdentifier:i, nodeId:Integer.parseInt(it,16))   
				}                            
			}
       }
    if (logEnable) log.debug "processAssociations cmds: ${cmds}"
    return cmds
}

def zwaveEvent(hubitat.zwave.commands.associationv2.AssociationReport cmd) {
    if (logEnable) log.debug "${device.label?device.label:device.name}: ${cmd}"
    def temp = []
    if (cmd.nodeId != []) {
       cmd.nodeId.each {
          temp += it.toString().format( '%02x', it.toInteger() ).toUpperCase()
       }
    }
    def zone=cmd.groupingIdentifier-1
    if (logEnable) log.debug "${cmd.groupingIdentifier} - $zone - $temp"
    if (zone > 0) {
        device.updateSetting("associationsZ$zone",[value: "${temp.toString().minus("[").minus("]")}", type: "string"])
    }
    updateDataValue("zwaveAssociationG${cmd.groupingIdentifier}", "$temp") 
}

def zwaveEvent(hubitat.zwave.commands.associationv2.AssociationGroupingsReport cmd) {
    if (logEnable) log.debug "${device.label?device.label:device.name}: ${cmd}"
    sendEvent(name: "groups", value: cmd.supportedGroupings)
    log.info "${device.label?device.label:device.name}: Supported association groups: ${cmd.supportedGroupings}"
    state.associationGroups = cmd.supportedGroupings
}
