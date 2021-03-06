/**
 *     Minoston Door/Window Sensor v1.0.0
 *
 *  	Models: MSE30Z/ZWS713
 *
 *  Author:
 *   winnie (sky-nie)
 *
 *	Documentation:
 *
 *  Changelog:
 *
 *    1.0.0 (04/26/2021)
 *      - Initial Release
 *
 * Reference：
 *   https://community.smartthings.com/t/release-aeotec-trisensor/140556?u=krlaframboise
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
metadata {
	definition(
			name: "Minoston Door/Window Sensor",
			namespace: "sky-nie",
			author: "winnie",
			ocfDeviceType: "x.com.st.d.sensor.contact",
		//	runLocally: true,
		//	minHubCoreVersion: '000.017.0012',
		//	executeCommandsLocally: false,
			genericHandler: "Z-Wave"
	) {
		capability "Sensor"
		capability "Contact Sensor"
		capability "Temperature Measurement"
		capability "Relative Humidity Measurement"
		capability "Battery"
		capability "Configuration"
		capability "Refresh"
		capability "Health Check"

		attribute "firmwareVersion", "string"
		attribute "lastCheckIn", "string"
		attribute "syncStatus", "string"

		//fingerprint mfr: "0312", prod: "0713", model: "D100", deviceJoinName: "Minoston Temp Humidity Sensor" //MSE30Z/ZWS713
	}

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
						backgroundColors:[
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

		standardTile("refresh", "device.refresh", width: 2, height: 2) {
			state "refresh", label:'Refresh', action: "refresh"
		}
		valueTile("syncStatus", "device.syncStatus", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "syncStatus", label:'${currentValue}'
		}

		standardTile("sync", "device.configure", width: 2, height: 2) {
			state "default", label: 'Sync', action: "configure"
		}
		valueTile("firmwareVersion", "device.firmwareVersion", decoration:"flat", width:3, height: 1) {
			state "firmwareVersion", label:'Firmware ${currentValue}'
		}
		valueTile("icon", "device.icon", inactiveLabel: false, decoration: "flat", width: 4, height: 1) {
			state "default", label: '', icon: "https://inovelli.com/wp-content/uploads/Device-Handler/Inovelli-Device-Handler-Logo.png"
		}
		main "contact", "temperature", "humidity"
		details(["contact", "temperature", "humidity", "battery", "refresh", "syncStatus", "sync", "firmwareVersion"])
	}

	simulator { }

	preferences {
		configParams.each {
			if (it.name) {
				if (it.range) {
					getNumberInput(it)
				}
				else {
					getOptionsInput(it)
				}
			}
		}
	}
}

private getOptionsInput(param) {
	input "configParam${param.num}", "enum",
			title: "${param.name}:",
			required: false,
			defaultValue: "${param.value}",
			options: param.options
}

private getNumberInput(param) {
	input "configParam${param.num}", "number",
			title: "${param.name}:",
			required: false,
			defaultValue: "${param.value}",
			range: param.range
}

def installed() {
	logDebug "installed()..."
    sendEvent(name: "checkInterval", value: checkInterval, displayed: false, data: [protocol: "zwave", hubHardwareId: device.hub.hardwareID])
	state.refreshConfig = true
}

private static def getCheckInterval() {
	// These are battery-powered devices, and it's not very critical
	// to know whether they're online or not – 12 hrs
	return (60 * 60 * 3) + (5 * 60)
}

def updated() {
	if (!isDuplicateCommand(state.lastUpdated, 5000)) {
		state.lastUpdated = new Date().time

		logDebug "updated()..."
		if (device.latestValue("checkInterval") != checkInterval) {
			sendEvent(name: "checkInterval", value: checkInterval, displayed: false)
		}

		runIn(5, executeConfigureCmds, [overwrite: true])
	}

	return []
}

def configure() {
	logDebug "configure()..."

	if (state.resyncAll == null) {
		state.resyncAll = true
		runIn(8, executeConfigureCmds, [overwrite: true])
	}
	else {
		if (!pendingChanges) {
			state.resyncAll = true
		}
		executeConfigureCmds()
	}
	return []
}

def executeConfigureCmds() {
	runIn(6, refreshSyncStatus)

	def cmds = []

//	cmds << sensorBinaryGetCmd()
//	cmds << batteryGetCmd()

	configParams.each { param ->
		def storedVal = getParamStoredValue(param.num)
		def paramVal = param.value
		if (state.resyncAll || ("${storedVal}" != "${paramVal}")) {
			cmds << configSetCmd(param, paramVal)
			if (param.num == minTemperatureOffsetParam.num) {
				cmds << "delay 3000"
				cmds << sensorMultilevelGetCmd(tempSensorType)
			}
			else if (param.num == minHumidityOffsetParam.num) {
				cmds << "delay 3000"
				cmds << sensorMultilevelGetCmd(lightSensorType)
			}else{
				cmds << configGetCmd(param)
			}
		}
	}

	state.resyncAll = false
	if (cmds) {
		sendCommands(delayBetween(cmds, 500))
	}
	return []
}

private getConfigCmds() {
	def cmds = []
	configParams.each { param ->
		def storedVal = getParamStoredValue(param.num)
		if (state.refreshConfig) {
			cmds << configGetCmd(param)
		}
		else if ("${storedVal}" != "${param.value}") {
			def paramVal = param.value
			logDebug "Changing ${param.name}(#${param.num}) from ${storedVal} to ${paramVal}"
			cmds << configSetCmd(param, paramVal)

			if (param.num == minTemperatureOffsetParam.num) {
				cmds << "delay 3000"
				cmds << sensorMultilevelGetCmd(tempSensorType)
			}
			else if (param.num == minHumidityOffsetParam.num) {
				cmds << "delay 3000"
				cmds << sensorMultilevelGetCmd(lightSensorType)
			}else{
				cmds << configGetCmd(param)
			}
		}
	}
	state.refreshConfig = false
	return cmds
}

private sendCommands(cmds) {
	def actions = []
	cmds?.each {
		actions << new physicalgraph.device.HubAction(it)
	}
	sendHubCommand(actions, 100)
	return []
}


// Required for HealthCheck Capability, but doesn't actually do anything because this device sleeps.
def ping() {
	logDebug "ping()"
}


// Forces the configuration to be resent to the device the next time it wakes up.
def refresh() {
	logForceWakeupMessage "The sensor data will be refreshed the next time the device wakes up."
	state.lastBattery = null
	if (!state.refreshSensors) {
		state.refreshSensors = true
	}
	else {
		state.refreshConfig = true
	}
	refreshSyncStatus()
	return []
}

private logForceWakeupMessage(msg) {
	logDebug "${msg}  You can force the device to wake up immediately by holding the z-button for 2 seconds."
}


def parse(String description) {
	def result = []
	try {
		def cmd = zwave.parse(description, commandClassVersions)
		if (cmd) {
			result += zwaveEvent(cmd)
		}
		else {
			logDebug "Unable to parse description: $description"
		}

		sendEvent(name: "lastCheckIn", value: convertToLocalTimeString(new Date()), displayed: false)
	}
	catch (e) {
		log.error "$e"
	}
	return result
}


def zwaveEvent(physicalgraph.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
	def encapCmd = cmd.encapsulatedCommand(commandClassVersions)

	def result = []
	if (encapCmd) {
		result += zwaveEvent(encapCmd)
	}
	else {
		log.warn "Unable to extract encapsulated cmd from $cmd"
	}
	return result
}


def zwaveEvent(physicalgraph.zwave.commands.wakeupv1.WakeUpNotification cmd) {
	logDebug "Device Woke Up"

	def cmds = []
	if (state.refreshConfig || pendingChanges > 0) {
		cmds += getConfigCmds()
	}

	if (canReportBattery()) {
		cmds << batteryGetCmd()
	}

	if (state.refreshSensors) {
		cmds += [
				sensorBinaryGetCmd(),
				sensorMultilevelGetCmd(tempSensorType),
				sensorMultilevelGetCmd(lightSensorType)
		]
		state.refreshSensors = false
	}

	if (cmds) {
		cmds = delayBetween(cmds, 1000)
		cmds << "delay 3000"
	}
	cmds << wakeUpNoMoreInfoCmd()
	return response(cmds)
}


def zwaveEvent(physicalgraph.zwave.commands.batteryv1.BatteryReport cmd) {
	def val = (cmd.batteryLevel == 0xFF ? 1 : cmd.batteryLevel)
	if (val > 100) {
		val = 100
	}
	else if (val < 1) {
		val = 1
	}
	state.lastBattery = new Date().time

	logDebug "Battery ${val}%"
	sendEvent(getEventMap("battery", val, null, null, "%"))
	return []
}


def zwaveEvent(physicalgraph.zwave.commands.sensormultilevelv5.SensorMultilevelReport cmd) {
	logTrace "SensorMultilevelReport: ${cmd}"

	if (cmd.sensorValue != [255, 255]) { // Bug in beta device
		switch (cmd.sensorType) {
			case tempSensorType:
				def unit = cmd.scale ? "F" : "C"
				def temp = convertTemperatureIfNeeded(cmd.scaledSensorValue, unit, cmd.precision)

				sendEvent(getEventMap("temperature", temp, true, null, getTemperatureScale()))
				break

			case lightSensorType:
				sendEvent(getEventMap( "humidity", cmd.scaledSensorValue, true, null, "%"))
				break
			default:
				logDebug "Unknown Sensor Type: ${cmd.sensorType}"
		}
	}
	return []
}


def zwaveEvent(physicalgraph.zwave.commands.configurationv1.ConfigurationReport cmd) {
	logTrace "ConfigurationReport ${cmd}"

	updateSyncingStatus()
	runIn(4, refreshSyncStatus)

	def param = configParams.find { it.num == cmd.parameterNumber }
	if (param) {
		def val = cmd.scaledConfigurationValue

		logDebug "${param.name}(#${param.num}) = ${val}"
		setParamStoredValue(param.num, val)
	}
	else {
		logDebug "Parameter #${cmd.parameterNumber} = ${cmd.configurationValue}"
	}
	return []
}
private updateSyncingStatus() {
	sendEventIfNew("syncStatus", "Syncing...", false)
}

def refreshSyncStatus() {
	def changes = pendingChanges
	sendEventIfNew("syncStatus", (changes ?  "${changes} Pending Changes" : "Synced"), false)
}


def zwaveEvent(physicalgraph.zwave.commands.notificationv3.NotificationReport cmd) {
	logTrace "NotificationReport: $cmd"
	def result = []

	if (cmd.notificationType == 0x06 && cmd.event == 0x16) {
		result << sensorValueEvent(1)
	} else if (cmd.notificationType == 0x06 && cmd.event == 0x17) {
		result << sensorValueEvent(0)
	} else if (cmd.notificationType == 0x07) {
		if (cmd.event == 0x00) {
			result << createEvent(descriptionText: "$device.displayName covering was restored", isStateChange: true)
			cmds = [zwave.batteryV1.batteryGet(), zwave.wakeUpV1.wakeUpNoMoreInformation()]
			result << response(commands(cmds, 1000))
		} else if (cmd.event == 0x01 || cmd.event == 0x02) {
			result << sensorValueEvent(1)
		} else if (cmd.event == 0x03) {
			result << createEvent(descriptionText: "$device.displayName covering was removed", isStateChange: true)
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

def zwaveEvent(physicalgraph.zwave.commands.sensorbinaryv2.SensorBinaryReport cmd) {
	logTrace "SensorBinaryReport: $cmd"
	def map = [:]
	map.value = cmd.sensorValue ? "open" : "closed"
	map.name = "contact"
	if (map.value == "open") {
		map.descriptionText = "${device.displayName} is open"
	}
	else {
		map.descriptionText = "${device.displayName} is closed"
	}
	createEvent(map)
}

void zwaveEvent(physicalgraph.zwave.commands.indicatorv1.IndicatorReport cmd) {
	logTrace "${cmd}"
}

def zwaveEvent(physicalgraph.zwave.Command cmd) {
	logDebug "Ignored Command: $cmd"
	return []
}

private getEventMap(name, value, displayed=null, desc=null, unit=null) {
	def isStateChange = (device.currentValue(name) != value)
	displayed = (displayed == null ? isStateChange : displayed)
	def eventMap = [
			name: name,
			value: value,
			displayed: displayed,
			isStateChange: isStateChange,
			descriptionText: desc ?: "${device.displayName} ${name} is ${value}"
	]

	if (unit) {
		eventMap.unit = unit
		eventMap.descriptionText = "${eventMap.descriptionText}${unit}"
	}
	if (displayed) {
		logDebug "${eventMap.descriptionText}"
	}
	return eventMap
}


private wakeUpNoMoreInfoCmd() {
	return secureCmd(zwave.wakeUpV1.wakeUpNoMoreInformation())
}

private batteryGetCmd() {
	return secureCmd(zwave.batteryV1.batteryGet())
}

private sensorBinaryGetCmd() {
	return secureCmd(zwave.sensorBinaryV2.sensorBinaryGet())
}

private sensorMultilevelGetCmd(sensorType) {
	def scale = (sensorType == tempSensorType) ? 0 : 1
	return secureCmd(zwave.sensorMultilevelV5.sensorMultilevelGet(scale: scale, sensorType: sensorType))
}

private configGetCmd(param) {
	return secureCmd(zwave.configurationV1.configurationGet(parameterNumber: param.num))
}

private configSetCmd(param, value) {
	return secureCmd(zwave.configurationV1.configurationSet(parameterNumber: param.num, size: param.size, scaledConfigurationValue: value))
}

private secureCmd(cmd) {
	try {
		if (zwaveInfo?.zw?.contains("s") || ("0x98" in device?.rawDescription?.split(" "))) {
			return zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format()
		}
		else {
			return cmd.format()
		}
	}catch (ex) {
		return cmd.format()
	}
}


private static getCommandClassVersions() {
	[
			0x30: 2,  // SensorBinary
			0x31: 5,  // SensorMultilevel
			0x55: 1,  // TransportServices
			0x59: 1,  // AssociationGrpInfo
			0x5A: 1,  // DeviceResetLocally
			0x5E: 2,  // ZwaveplusInfo
			0x6C: 1,  // Supervision
			0x70: 1,  // Configuration
			0x71: 3,  // Notification
			0x72: 2,  // ManufacturerSpecific
			0x73: 1,  // Powerlevel
			0x7A: 2,  // FirmwareUpdateMd
			0x80: 1,  // Battery
			0x84: 1,  // WakeUp
			0x85: 2,  // Association
			0x86: 1,  // Version
			0x8E: 2,  // MultChannelAssociation
			0x87: 1,  // Indicator
			0x9F: 1   // Security 2
	]
}


private canReportBattery() {
	return state.refreshSensors || (!isDuplicateCommand(state.lastBattery, (12 * 60 * 60 * 1000)))
}

private getPendingChanges() {
	return configParams.count { "${it.value}" != "${getParamStoredValue(it.num)}" }
}

private getParamStoredValue(paramNum) {
	return safeToInt(state["configParam${paramNum}"] , null)
}

private setParamStoredValue(paramNum, value) {
	state["configParam${paramNum}"] = value
}


// Sensor Types
private static getTempSensorType() { return 1 }
private static getLightSensorType() { return 5 }


// Configuration Parameters
private getConfigParams() {
	[
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
			associationGroupSettingParam
	]
}

private getBatteryReportThresholdParam() {
	return getParam(1, "Battery report threshold\n(1% - 20%)", 1, 10, null,"1..20")
}

private getLowBatteryAlarmReportParam() {
	return getParam(2, "Low battery alarm report\n(5% - 20%)", 1, 5, null, "5..20")
}

private getSensorModeWhenClosedParam() {
	return getParam(3, "State of the sensor when the magnet closes the reed", 1, 0, sensorModeWhenCloseOptions)
}

private getDelayReportSecondsWhenClosedParam() {
	return getParam(4, "Delay in seconds with ON command report(door closed)", 2, 0, null, "0..3600")
}

private getDelayReportSecondsWhenOpenedParam() {
	return getParam(5, "Delay in seconds with OFF command report(door open)", 2, 0, null, "0..3600")
}

private getMinTemperatureOffsetParam() {
	return getParam(6, "Minimum Temperature change to report", 1, 3, minTemperatureOffsetOptions)
}

private getMinHumidityOffsetParam() {
	return getParam(7, "Minimum Humidity change to report\n(5% - 20%)", 1, 10, null, "5..20")
}

private getTemperatureUpperWatermarkParam() {
	return getParam(8, "Temperature Upper Watermark value\n(0,Disabled; 1℃/33.8°F-50℃/122.0°F)", 2, 0, null, "0..50")
}

private getTemperatureLowerWatermarkParam() {
	return getParam(9, "Temperature Lower Watermark value\n(0,Disabled; 1℃/33.8°F-50℃/122.0°F)", 2, 0, null, "0..50")
}

private getHumidityUpperWatermarkParam() {
	return getParam(10, "Humidity Upper Watermark value\n(0,Disabled; 1%-100%)", 1, 0, null, "0..100")
}

private getHumidityLowerWatermarkParam() {
	return getParam(11, "Humidity Lower Watermark value\n(0,Disabled; 1%-100%)", 1, 0, null, "0..100")
}

private getSwitchTemperatureUnitParam() {
	return getParam(12, "Switch the unit of Temperature report", 1, 1,  switchTemperatureUnitOptions)
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

private getParam(num, name, size, defaultVal, options=null, range=null) {
	def val = safeToInt((settings ? settings["configParam${num}"] : null), defaultVal)

	def map = [num: num, name: name, size: size, value: val]
	if (options) {
		map.valueName = options?.find { k, v -> "${k}" == "${val}" }?.value
		map.options = setDefaultOption(options, defaultVal)
	}
	if (range) map.range = range

	return map
}

private static setDefaultOption(options, defaultVal) {
	return options?.collectEntries { k, v ->
		if ("${k}" == "${defaultVal}") {
			v = "${v} [DEFAULT]"
		}
		["$k": "$v"]
	}
}

// Setting Options
private static getSwitchTemperatureUnitOptions() {
	return [
			"0":"Celsius",
			"1":"Fahrenheit"
	]
}

private static getSensorModeWhenCloseOptions() {
	return [
			"0":"door/window closed",
			"1":"door/window opened"
	]
}

private static getMinTemperatureOffsetOptions() {
	def options = [:]
	options["1"] = "1℃/1.8°F"
	def it1, it2

	(2..9).each {
		it1 = (it + 1)*0.5
		it2 = (it + 1)*0.9
		options["${it}"] = "${it1}℃/${it2}°F"
	}
	return options
}

private sendEventIfNew(name, value, displayed=true, type=null, unit="") {
	def desc = "${name} is ${value}${unit}"
	if (device.currentValue(name) != value) {
		logDebug(desc)

		def evt = [name: name, value: value, descriptionText: "${device.displayName} ${desc}", displayed: displayed]

		if (type) {
			evt.type = type
		}
		if (unit) {
			evt.unit = unit
		}
		sendEvent(evt)
	}
	else {
		logTrace(desc)
	}
}

//def ledIndicatorOn() {
//	return delayBetween([
//			indicatorSetCmd(0xFF),
//			indicatorGetCmd()
//	], 300)
//}
//
//def ledIndicatorOff() {
//	return delayBetween([
//			indicatorSetCmd(0x00),
//			indicatorGetCmd()
//	], 300)
//}
//
//private String indicatorGetCmd() {
//	return secureCmd(zwave.indicatorV1.indicatorGet())
//}
//
//private String indicatorSetCmd(int value) {
//	return secureCmd(zwave.indicatorV1.indicatorSet(value: value))
//}

def sensorValueEvent(value) {
	if (value) {
		createEvent(name: "contact", value: "open", descriptionText: "$device.displayName is open")
	} else {
		createEvent(name: "contact", value: "closed", descriptionText: "$device.displayName is closed")
	}
}

private static safeToInt(val, defaultVal=0) {
	return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal
}

private convertToLocalTimeString(dt) {
	def timeZoneId = location?.timeZone?.ID
	if (timeZoneId) {
		return dt.format("MM/dd/yyyy hh:mm:ss a", TimeZone.getTimeZone(timeZoneId))
	}
	else {
		return "$dt"
	}
}

private static isDuplicateCommand(lastExecuted, allowedMil) {
	!lastExecuted ? false : (lastExecuted + allowedMil > new Date().time)
}

private logDebug(msg) {
	log.debug "$msg"
}

private logTrace(msg) {
	log.trace "$msg"
}
