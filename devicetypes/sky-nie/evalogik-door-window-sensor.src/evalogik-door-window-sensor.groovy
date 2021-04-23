/**
 *     Evalogik Door/Window Sensor v1.0.0
 *
 *  	Models: ZWS713
 *
 *  Author:
 *   winnie (sky-nie)
 *
 *	Documentation:
 *
 *  Changelog:
 *
 *    1.0.0 (04/11/2021)
 *      - Initial Release
 *
 * Reference：
 *   1.Z-Wave Door/Window Sensor
 *   2.SmartSense Temp/Humidity Sensor
 *   3.Dome Door/Window Sensor Pro(Kevin LaFramboise)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 */

metadata {
	definition(
	name: "Evalogik Door/Window Sensor",
	namespace: "sky-nie", 
	author: "winnie", 
	ocfDeviceType: "x.com.st.d.sensor.contact", 
	runLocally: true, 
	minHubCoreVersion: '000.017.0012', 
	executeCommandsLocally: false, 
	genericHandler: "Z-Wave"
	) {
		capability "Contact Sensor"
		capability "Sensor"
		capability "Battery"
		capability "Configuration"
		capability "Health Check"
		capability "Refresh"
		capability "Temperature Measurement"
		capability "Relative Humidity Measurement"

		fingerprint mfr: "0312", prod: "0713", model: "D100", deviceJoinName: "Minoston Temp Humidity Sensor" //ZWS713
	}

	// simulator metadata
	simulator {
		// status messages
		status "open": "command: 2001, payload: FF"
		status "closed": "command: 2001, payload: 00"
		status "wake up": "command: 8407, payload: "
		status 'H 40': 'catchall: 0104 FC45 01 01 0140 00 D9B9 00 04 C2DF 0A 01 000021780F'
		status 'H 45': 'catchall: 0104 FC45 01 01 0140 00 D9B9 00 04 C2DF 0A 01 0000218911'
		status 'H 57': 'catchall: 0104 FC45 01 01 0140 00 4E55 00 04 C2DF 0A 01 0000211316'
		status 'H 53': 'catchall: 0104 FC45 01 01 0140 00 20CD 00 04 C2DF 0A 01 0000219814'
		status 'H 43': 'read attr - raw: BF7601FC450C00000021A410, dni: BF76, endpoint: 01, cluster: FC45, size: 0C, attrId: 0000, result: success, encoding: 21, value: 10a4'
	}

	preferences {
		getParamInput(batteryReportThresholdParam)
		getParamInput(lowBatteryAlarmReportParam)
		getParamInput(delayReportSecondsWhenClosedParam)
		getParamInput(delayReportSecondsWhenOpenedParam)
		getParamInput(minHumidityOffsetParam)
		getParamInput(temperatureUpperWatermarkParam)
		getParamInput(temperatureLowerWatermarkParam)
		getParamInput(humidityUpperWatermarkParam)
		getParamInput(humidityLowerWatermarkParam)

		configParams0.each {
			createEnumInput("configParam${it.num}", "${it.name}:", it.value, it.options)
		}
	}

	// UI tile definitions
	tiles(scale: 2) {
		multiAttributeTile(name: "contact", type: "generic", width: 6, height: 4) {
			tileAttribute("device.contact", key: "PRIMARY_CONTROL") {
				attributeState("open", label: '${name}', icon: "st.contact.contact.open", backgroundColor: "#e86d13")
				attributeState("closed", label: '${name}', icon: "st.contact.contact.closed", backgroundColor: "#00A0DC")
			}
		}

		multiAttributeTile(name: "temperature", type: "generic", width: 6, height: 4, canChangeIcon: true) {
			tileAttribute("device.temperature", key: "PRIMARY_CONTROL") {
				attributeState "temperature", label: '${currentValue}°',
						backgroundColors: [
								[value: 31, color: "#153591"],
								[value: 44, color: "#1e9cbb"],
								[value: 59, color: "#90d2a7"],
								[value: 74, color: "#44b621"],
								[value: 84, color: "#f1d801"],
								[value: 95, color: "#d04e00"],
								[value: 96, color: "#bc2323"]
						]
			}
		}
		valueTile("humidity", "device.humidity", inactiveLabel: false, width: 2, height: 2) {
			state "humidity", label: '${currentValue}% humidity', unit: ""
		}

		valueTile("battery", "device.battery", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "battery", label: '${currentValue}% battery', unit: ""
		}

		standardTile("refresh", "device.refresh", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "default", action: "refresh.refresh", icon: "st.secondary.refresh"
		}

		main "contact", "temperature", "humidity"
		details(["contact", "temperature", "humidity", "battery", "refresh"])
	}
}
private createEnumInput(name, title, defaultVal, options) {
	input name, "enum",
			title: title,
			required: false,
			defaultValue: defaultVal.toString(),
			options: options
}
private getParamInput(param) {
	input "${param.prefName}", "number",
			title: "${param.name}:",
			defaultValue: "${param.val}",
			required: false,
			displayDuringSetup: true,
			range: "${param.range}"
}

private getCommandClassVersions() {
	[0x20: 1, 0x25: 1, 0x30: 1, 0x31: 5, 0x80: 1, 0x84: 1, 0x71: 3, 0x9C: 1]
}

def parse(String description) {
	def result = null
	if (description.startsWith("Err 106")) {
		if ((zwaveInfo.zw == null && state.sec != 0) || zwaveInfo?.zw?.contains("s")) {
			log.debug description
		} else {
			result = createEvent(
					descriptionText: "This sensor failed to complete the network security key exchange. If you are unable to control it via SmartThings, you must remove it from your network and add it again.",
					eventType: "ALERT",
					name: "secureInclusion",
					value: "failed",
					isStateChange: true,
			)
		}
	} else if (description != "updated") {
		def cmd = zwave.parse(description, commandClassVersions)
		if (cmd) {
			result = zwaveEvent(cmd)
		}
	}
	log.debug "parsed '$description' to $result"
	return result
}

def installed() {
	// Device-Watch simply pings if no device events received for 482min(checkInterval)
	sendEvent(name: "checkInterval", value: 2 * 4 * 60 * 60 + 2 * 60, displayed: false, data: [protocol: "zwave", hubHardwareId: device.hub.hardwareID])
	// this is the nuclear option because the device often goes to sleep before we can poll it
	sendEvent(name: "contact", value: "open", descriptionText: "$device.displayName is open")
	sendEvent(name: "battery", unit: "%", value: 100)
	//sendEvent(name: "temperature", value: 68, unit: getTemperatureScale(), displayed: false)
	//sendEvent(name: "humidity", value: 10, unit: "%", displayed: false)
	sendEvent(name: "tamper", value: "clear")
	response(initialPoll())
}

def updated() {
	// Device-Watch simply pings if no device events received for 482min(checkInterval)
	sendEvent(name: "checkInterval", value: 2 * 4 * 60 * 60 + 2 * 60, displayed: false, data: [protocol: "zwave", hubHardwareId: device.hub.hardwareID])
}

def configure() {
	//Recessed Door Sensor 7 - Enable Binary Sensor Report for S2 Authenticated
	if (zwaveInfo.mfr == "0371" || zwaveInfo.model == "00BB") {
		result << response(command(zwave.configurationV1.configurationSet(parameterNumber: 1, size: 1, scaledConfigurationValue: 1)))
		result
	}
}

def sensorValueEvent(value) {
	if (value) {
		createEvent(name: "contact", value: "open", descriptionText: "$device.displayName is open")
	} else {
		createEvent(name: "contact", value: "closed", descriptionText: "$device.displayName is closed")
	}
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicReport cmd) {
	sensorValueEvent(cmd.value)
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicSet cmd) {
	sensorValueEvent(cmd.value)
}

def zwaveEvent(physicalgraph.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd) {
	sensorValueEvent(cmd.value)
}

def zwaveEvent(physicalgraph.zwave.commands.sensorbinaryv1.SensorBinaryReport cmd) {
	sensorValueEvent(cmd.sensorValue)
}

def zwaveEvent(physicalgraph.zwave.commands.sensoralarmv1.SensorAlarmReport cmd) {
	sensorValueEvent(cmd.sensorState)
}

def zwaveEvent(physicalgraph.zwave.commands.notificationv3.NotificationReport cmd) {
	def result = []
	if (cmd.notificationType == 0x06 && cmd.event == 0x16) {
		result << sensorValueEvent(1)
	} else if (cmd.notificationType == 0x06 && cmd.event == 0x17) {
		result << sensorValueEvent(0)
	} else if (cmd.notificationType == 0x07) {
		if (cmd.v1AlarmType == 0x07) {  // special case for nonstandard messages from Monoprice door/window sensors
			result << sensorValueEvent(cmd.v1AlarmLevel)
		}
//        else if (cmd.event == 0x00) {
//			result << createEvent(name: "tamper", value: "clear")
//		} 
        else if (cmd.event == 0x01 || cmd.event == 0x02) {
			result << sensorValueEvent(1)
		} 
//        else if (cmd.event == 0x03) {
//			runIn(10, clearTamper, [overwrite: true, forceForLocallyExecuting: true])
//			result << createEvent(name: "tamper", value: "detected", descriptionText: "$device.displayName was tampered")
//			if (!state.MSR) result << response(command(zwave.manufacturerSpecificV2.manufacturerSpecificGet()))
//		} else if (cmd.event == 0x05 || cmd.event == 0x06) {
//			result << createEvent(descriptionText: "$device.displayName detected glass breakage", isStateChange: true)
//		}
        else if (cmd.event == 0x07) {
			if (!state.MSR) result << response(command(zwave.manufacturerSpecificV2.manufacturerSpecificGet()))
			result << createEvent(name: "motion", value: "active", descriptionText: "$device.displayName detected motion")
		}
	} else if (cmd.notificationType) {
		def text = "Notification $cmd.notificationType: event ${([cmd.event] + cmd.eventParameter).join(", ")}"
		result << createEvent(name: "notification$cmd.notificationType", value: "$cmd.event", descriptionText: text, displayed: false)
	} else {
		def value = cmd.v1AlarmLevel == 255 ? "active" : cmd.v1AlarmLevel ?: "inactive"
		result << createEvent(name: "alarm $cmd.v1AlarmType", value: value, displayed: false)
	}
	result
}

def zwaveEvent(physicalgraph.zwave.commands.wakeupv1.WakeUpNotification cmd) {
	def event = createEvent(descriptionText: "${device.displayName} woke up", isStateChange: false)
	def cmds = []
	if (!state.MSR) {
		cmds << zwave.manufacturerSpecificV2.manufacturerSpecificGet()
	}

	if (device.currentValue("contact") == null) {
		// In case our initial request didn't make it, initial state check no. 3
		cmds << zwave.sensorBinaryV2.sensorBinaryGet(sensorType: zwave.sensorBinaryV2.SENSOR_TYPE_DOOR_WINDOW)
	}

	if (!state.lastbat || now() - state.lastbat > 53 * 60 * 60 * 1000) {
		cmds << zwave.batteryV1.batteryGet()
	}

	def request = []
	if (cmds.size() > 0) {
		request = commands(cmds, 1000)
		request << "delay 20000"
	}
	request << zwave.wakeUpV1.wakeUpNoMoreInformation().format()

	[event, response(request)]
}

def zwaveEvent(physicalgraph.zwave.commands.batteryv1.BatteryReport cmd) {
	def map = [name: "battery", unit: "%"]
	if (cmd.batteryLevel == 0xFF) {
		map.value = 1
		map.descriptionText = "${device.displayName} has a low battery"
		map.isStateChange = true
	} else {
		map.value = cmd.batteryLevel
	}
	state.lastbat = now()
	[createEvent(map)]
}

def zwaveEvent(physicalgraph.zwave.commands.manufacturerspecificv2.ManufacturerSpecificReport cmd) {
	def result = []

	def msr = String.format("%04X-%04X-%04X", cmd.manufacturerId, cmd.productTypeId, cmd.productId)
	log.debug "msr: $msr"
	updateDataValue("MSR", msr)

	result << createEvent(descriptionText: "$device.displayName MSR: $msr", isStateChange: false)

	// change DTH if required based on MSR
	if (!retypeBasedOnMSR()) {
		if (msr == "011A-0601-0901") {
			// Enerwave motion doesn't always get the associationSet that the hub sends on join
			result << response(zwave.associationV1.associationSet(groupingIdentifier: 1, nodeId: zwaveHubNodeId))
		}
	} else {
		// if this is door/window sensor check initial contact state no.2
		if (!device.currentState("contact")) {
			result << response(command(zwave.sensorBinaryV2.sensorBinaryGet(sensorType: zwave.sensorBinaryV2.SENSOR_TYPE_DOOR_WINDOW)))
		}
	}

	// every battery device can miss initial battery check. check initial battery state no.2
	if (!device.currentState("battery")) {
		result << response(command(zwave.batteryV1.batteryGet()))
	}

	result
}

def zwaveEvent(physicalgraph.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
	def encapsulatedCommand = cmd.encapsulatedCommand(commandClassVersions)
	if (encapsulatedCommand) {
		zwaveEvent(encapsulatedCommand)
	}
}

def zwaveEvent(physicalgraph.zwave.commands.crc16encapv1.Crc16Encap cmd) {
	def version = commandClassVersions[cmd.commandClass as Integer]
	def ccObj = version ? zwave.commandClass(cmd.commandClass, version) : zwave.commandClass(cmd.commandClass)
	def encapsulatedCommand = ccObj?.command(cmd.command)?.parse(cmd.data)
	if (encapsulatedCommand) {
		return zwaveEvent(encapsulatedCommand)
	}
}

def zwaveEvent(physicalgraph.zwave.commands.sensormultilevelv3.SensorMultilevelReport cmd) {
	def map = [:]
	if (cmd.sensorType == 1) {
		map.value = getTempInLocalScale(cmd.scaledSensorValue, cmd.scale == 1 ? "F" : "C")
		map.unit = getTemperatureScale()
		map.name = "temperature"
	} else if (cmd.sensorType == 5) {
		map.value = cmd.scaledSensorValue
		map.unit = "%"
		map.name = "humidity"
	}
	createEvent(map)
}
def zwaveEvent(physicalgraph.zwave.commands.multichannelv3.MultiChannelCmdEncap cmd) {
	def result = null
	if (cmd.commandClass == 0x6C && cmd.parameter.size >= 4) { // Supervision encapsulated Message
		// Supervision header is 4 bytes long, two bytes dropped here are the latter two bytes of the supervision header
		cmd.parameter = cmd.parameter.drop(2)
		// Updated Command Class/Command now with the remaining bytes
		cmd.commandClass = cmd.parameter[0]
		cmd.command = cmd.parameter[1]
		cmd.parameter = cmd.parameter.drop(2)
	}
	def encapsulatedCommand = cmd.encapsulatedCommand(commandClassVersions)
	log.debug "Command from endpoint ${cmd.sourceEndPoint}: ${encapsulatedCommand}"
	if (encapsulatedCommand) {
		result = zwaveEvent(encapsulatedCommand)
	}
	result
}

def zwaveEvent(physicalgraph.zwave.commands.multicmdv1.MultiCmdEncap cmd) {
	log.debug "MultiCmd with $numberOfCommands inner commands"
	cmd.encapsulatedCommands(commandClassVersions).collect { encapsulatedCommand ->
		zwaveEvent(encapsulatedCommand)
	}.flatten()
}

def zwaveEvent(physicalgraph.zwave.Command cmd) {
	createEvent(descriptionText: "$device.displayName: $cmd", displayed: false)
}

def initialPoll() {
	def request = []
	if (isEnerwave()) { // Enerwave motion doesn't always get the associationSet that the hub sends on join
		request << zwave.associationV1.associationSet(groupingIdentifier: 1, nodeId: zwaveHubNodeId)
	}

	// check initial battery and contact state no.1
	request << zwave.batteryV1.batteryGet()
	request << zwave.sensorBinaryV2.sensorBinaryGet(sensorType: zwave.sensorBinaryV2.SENSOR_TYPE_DOOR_WINDOW)
	request << zwave.manufacturerSpecificV2.manufacturerSpecificGet()
	commands(request, 500) + ["delay 6000", command(zwave.wakeUpV1.wakeUpNoMoreInformation())]
}

private command(physicalgraph.zwave.Command cmd) {
	if ((zwaveInfo?.zw == null && state.sec != 0) || zwaveInfo?.zw?.contains("s")) {
		zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format()
	} else {
		cmd.format()
	}
}

private commands(commands, delay = 200) {
	delayBetween(commands.collect { command(it) }, delay)
}

def retypeBasedOnMSR() {
	def dthChanged = true
	switch (state.MSR) {
		case "0086-0002-002D":
			log.debug "Changing device type to Z-Wave Water Sensor"
			setDeviceType("Z-Wave Water Sensor")
			break
		case "011F-0001-0001":  // Schlage motion
		case "014A-0001-0001":  // Ecolink motion
		case "014A-0004-0001":  // Ecolink motion +
		case "0060-0001-0002":  // Everspring SP814
		case "0060-0001-0003":  // Everspring HSP02
		case "011A-0601-0901":  // Enerwave ZWN-BPC
			log.debug "Changing device type to Z-Wave Motion Sensor"
			setDeviceType("Z-Wave Motion Sensor")
			break
		case "013C-0002-000D":  // Philio multi +
			log.debug "Changing device type to 3-in-1 Multisensor Plus (SG)"
			setDeviceType("3-in-1 Multisensor Plus (SG)")
			break
		case "0109-2001-0106":  // Vision door/window
			log.debug "Changing device type to Z-Wave Plus Door/Window Sensor"
			setDeviceType("Z-Wave Plus Door/Window Sensor")
			break
		case "0109-2002-0205": // Vision Motion
			log.debug "Changing device type to Z-Wave Plus Motion/Temp Sensor"
			setDeviceType("Z-Wave Plus Motion/Temp Sensor")
			break
		default:
			dthChanged = false
			break
	}
	dthChanged
}

// this is present in zwave-motion-sensor.groovy DTH too
private isEnerwave() {
	zwaveInfo?.mfr?.equals("011A") && zwaveInfo?.prod?.equals("0601") && zwaveInfo?.model?.equals("0901")
}

def clearTamper() {
	sendEvent(name: "tamper", value: "clear")
}

private getConfigParams0() {
	return [
			sensorModeWhenClosedParam,
			minTemperatureOffsetParam,
			switchTemperatureUnitParam,
			associationGroupSettingParam,
	]
}

private getConfigParams() {
	return [
			batteryReportThresholdParam,
			lowBatteryAlarmReportParam,
			sensorModeWhenClosedParam,
			delayReportSecondsWhenClosedParam,
			delayReportSecondsWhenOpenedParam,
			minTemperatureOffsetParam,
			minHumidityOffsetParam,
			temperatureUpperWatermarkParam,
			temperatureLowerWatermarkParam,
			humidityUpperWatermarkParam,
			humidityLowerWatermarkParam,
			switchTemperatureUnitParam,
			associationGroupSettingParam,
	]
}

private getBatteryReportThresholdParam() {
	return createConfigParamMap(1, "Battery report threshold\n(1% - 20%)", 1, "batteryTrigger", "1..20", 10)
}

private getLowBatteryAlarmReportParam() {
	return createConfigParamMap(2, "Low battery alarm report\n(5% - 20%)", 1, "lowBatteryTrigger", "5..20", 5)
}

private getSensorModeWhenClosedParam() {
	return getParam(3, "State of the sensor when the magnet closes the reed", 1, 0, [0:"door/window closed", 1:"door/window opened"])
}

private getDelayReportSecondsWhenClosedParam() {
	return createConfigParamMap(4, "Delay in seconds with ON command report(door closed)", 2, "delaySeconds", "0..3600", 0)
}

private getDelayReportSecondsWhenOpenedParam() {
	return createConfigParamMap(5, "Delay in seconds with OFF command report(door open)", 2, "delaySeconds", "0..3600", 0)
}

private getMinTemperatureOffsetParam() {
	return getParam(6, "Minimum Temperature change to report", 1, 3, [1:"1℃/1.8°F", 2:"1.5℃/2.7°F", 3:"2℃/3.6°F", 4:"2.5℃/4.5°F", 5:"3℃/5.4°F", 6:"3.5℃/6.3°F", 7:"4℃/7.2°F", 8:"4.5℃/8.1°F", 9:"5℃/9.0°F"])
}

private getMinHumidityOffsetParam() {
	return createConfigParamMap(7, "Minimum Humidity change to report\n(5% - 20%)", 1, "batteryTrigger", "5..20", 10)
}

private getTemperatureUpperWatermarkParam() {
	return createConfigParamMap(8, "Temperature Upper Watermark value\n(0,Disabled; 1℃/33.8°F-50℃/122.0°F)", 2, "temperatureWatermark", "0..50", 0)
}

private getTemperatureLowerWatermarkParam() {
	return createConfigParamMap(9, "Temperature Lower Watermark value\n(0,Disabled; 1℃/33.8°F-50℃/122.0°F)", 2, "temperatureWatermark", "0..50", 0)
}

private getHumidityUpperWatermarkParam() {
	return createConfigParamMap(10, "Humidity Upper Watermark value\n(0,Disabled; 1%-100%)", 1, "humidityWatermark", "0..100", 0)
}

private getHumidityLowerWatermarkParam() {
	return createConfigParamMap(11, "Humidity Lower Watermark value\n(0,Disabled; 1%-100%)", 1, "humidityWatermark", "0..100", 0)
}

private getSwitchTemperatureUnitParam() {
	return getParam(12, "Switch the unit of Temperature report", 1, 1, [0:"Celsius", 1:"Fahrenheit"])
}

private getAssociationGroupSettingParam() {
	return getParam(13, "Association Group 2 Setting", 1, 1, [0:"Disable completely",
															  1:"Send Basic SET 0xFF when Magnet is away,and send Basic SET 0x00 when Magnet is near.",
															  2:"Send Basic SET 0x00 when Magnet is away,and send Basic SET 0xFF when Magnet is near",
															  3:"Only send Basic SET 0xFF when Magnet is away",
															  4:"Only send Basic SET 0x00 when Magnet is near",
															  5:"Only send Basic SET 0x00 when Magnet is away",
															  6:"Only send Basic SET 0xFF when Magnet is near"
	])
}

private getParam(num, name, size, defaultVal, options) {
	def val = safeToInt((settings ? settings["configParam${num}"] : null), defaultVal)
	def map = [num: num, name: name, size: size, value: val]
	if (options) {
		map.options = setDefaultOption(options, defaultVal)
	}
	return map
}

private setDefaultOption(options, defaultVal) {
	return options?.collectEntries { k, v ->
		if ("${k}" == "${defaultVal}") {
			v = "${v} [DEFAULT]"
		}
		["$k": "$v"]
	}
}

private safeToInt(val, defaultVal=0) {
	return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal
}

private createConfigParamMap(num, name, size, prefName, range, val) {
	if (settings?."${prefName}" != null) {
		val = settings?."${prefName}"
	}
	return [
			num: num,
			name: name,
			size: size,
			prefName: prefName,
			range: range,
			val: val
	]
}