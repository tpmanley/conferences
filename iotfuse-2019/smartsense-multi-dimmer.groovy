/*
 *  Copyright 2016 SmartThings
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not
 *  use this file except in compliance with the License. You may obtain a copy
 *  of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *  WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *  License for the specific language governing permissions and limitations
 *  under the License.
 */
import physicalgraph.zigbee.clusters.iaszone.ZoneStatus
import physicalgraph.zigbee.zcl.DataType

metadata {
	definition(name: "SmartSense Multi Dimmer", namespace: "smartthings", author: "SmartThings", ocfDeviceType: "oic.d.light", runLocally: false, minHubCoreVersion: '000.017.0012', executeCommandsLocally: false, mnmn: "SmartThings", vid: "generic-dimmer") {

		capability "Three Axis"
		capability "Battery"
		capability "Configuration"
		capability "Sensor"
		capability "Contact Sensor"
		capability "Acceleration Sensor"
		capability "Refresh"
		capability "Temperature Measurement"
		capability "Health Check"
		capability "Switch"       // Example map: [name: "switch", value: "off"] where value can be "off" or "on"
		capability "Switch Level" // Example map: [name: "level", value: 0] where value is 0-100

		command "enrollResponse"

		//////////////////////////////////////////////////
		// TODO: update the fingerprint from the first DTH
		fingerprint profileId: "0104", manufacturer: "Samjin", model: "multi-XXXX", deviceJoinName: "multi-XXXX"
		//////////////////////////////////////////////////
	}

	preferences {
		section {
			image(name: 'educationalcontent', multiple: true, images: [
					"http://cdn.device-gse.smartthings.com/Multi/Multi1.jpg",
					"http://cdn.device-gse.smartthings.com/Multi/Multi2.jpg",
					"http://cdn.device-gse.smartthings.com/Multi/Multi3.jpg",
					"http://cdn.device-gse.smartthings.com/Multi/Multi4.jpg"
			])
		}
		section {
			input title: "Temperature Offset", description: "This feature allows you to correct any temperature variations by selecting an offset. Ex: If your sensor consistently reports a temp that's 5 degrees too warm, you'd enter '-5'. If 3 degrees too cold, enter '+3'.", displayDuringSetup: false, type: "paragraph", element: "paragraph"
			input "tempOffset", "number", title: "Degrees", description: "Adjust temperature by this many degrees", range: "*..*", displayDuringSetup: false
		}
	}

	tiles(scale: 2) {
		multiAttributeTile(name:"switch", type: "lighting", width: 6, height: 4, canChangeIcon: true){
			tileAttribute ("device.switch", key: "PRIMARY_CONTROL") {
				attributeState "on", label:'${name}', action:"switch.off", icon:"st.switches.light.on", backgroundColor:"#00A0DC", nextState:"turningOff"
				attributeState "off", label:'${name}', action:"switch.on", icon:"st.switches.light.off", backgroundColor:"#ffffff", nextState:"turningOn"
				attributeState "turningOn", label:'${name}', action:"switch.off", icon:"st.switches.light.on", backgroundColor:"#00A0DC", nextState:"turningOff"
				attributeState "turningOff", label:'${name}', action:"switch.on", icon:"st.switches.light.off", backgroundColor:"#ffffff", nextState:"turningOn"
			}
			tileAttribute ("device.level", key: "SLIDER_CONTROL") {
				attributeState "level", action:"switch level.setLevel"
			}
		}
		standardTile("contact", "device.contact", width: 2, height: 2) {
			state("open", label: 'Open', icon: "st.contact.contact.open", backgroundColor: "#e86d13")
			state("closed", label: 'Closed', icon: "st.contact.contact.closed", backgroundColor: "#00a0dc")
		}
		standardTile("acceleration", "device.acceleration", width: 2, height: 2) {
			state("active", label: 'Active', icon: "st.motion.acceleration.active", backgroundColor: "#00a0dc")
			state("inactive", label: 'Inactive', icon: "st.motion.acceleration.inactive", backgroundColor: "#cccccc")
		}
		valueTile("temperature", "device.temperature", width: 2, height: 2) {
			state("temperature", label: '${currentValue}°',
					backgroundColors: [
							[value: 31, color: "#153591"],
							[value: 44, color: "#1e9cbb"],
							[value: 59, color: "#90d2a7"],
							[value: 74, color: "#44b621"],
							[value: 84, color: "#f1d801"],
							[value: 95, color: "#d04e00"],
							[value: 96, color: "#bc2323"]
					]
			)
		}
		valueTile("battery", "device.battery", decoration: "flat", inactiveLabel: false, width: 2, height: 2) {
			state "battery", label: '${currentValue}% battery', unit: ""
		}
		standardTile("refresh", "device.refresh", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "default", action: "refresh.refresh", icon: "st.secondary.refresh"
		}


		main(["switch", "temperature"])
		details(["switch", "temperature", "battery", "refresh"])
	}
}

private List<Map> collectAttributes(Map descMap) {
	List<Map> descMaps = new ArrayList<Map>()

	descMaps.add(descMap)

	if (descMap.additionalAttrs) {
		descMaps.addAll(descMap.additionalAttrs)
	}

	return  descMaps
}

def parse(String description) {
	def maps = []
	maps << zigbee.getEvent(description)
	if (!maps[0]) {
		maps = []
		if (description?.startsWith('zone status')) {
			maps += parseIasMessage(description)
		} else {
			Map descMap = zigbee.parseDescriptionAsMap(description)

			if (descMap?.clusterInt == zigbee.POWER_CONFIGURATION_CLUSTER && descMap.commandInt != 0x07 && descMap.value) {
				List<Map> descMaps = collectAttributes(descMap)
				def battMap = descMaps.find { it.attrInt == 0x0021 }

				if (battMap) {
					maps += getBatteryPercentageResult(Integer.parseInt(battMap.value, 16))
				}
			} else if (descMap?.clusterInt == 0x0500 && descMap.attrInt == 0x0002) {
				def zs = new ZoneStatus(zigbee.convertToInt(descMap.value, 16))
				maps += translateZoneStatus(zs)
			} else if (descMap?.clusterInt == zigbee.TEMPERATURE_MEASUREMENT_CLUSTER && descMap.commandInt == 0x07) {
				if (descMap.data[0] == "00") {
					log.debug "TEMP REPORTING CONFIG RESPONSE: $descMap"
					sendEvent(name: "checkInterval", value: 60 * 12, displayed: false, data: [protocol: "zigbee", hubHardwareId: device.hub.hardwareID, offlinePingable: "1"])
				} else {
					log.warn "TEMP REPORTING CONFIG FAILED- error code: ${descMap.data[0]}"
				}
			} else if (descMap?.clusterInt == zigbee.IAS_ZONE_CLUSTER && descMap.attrInt == zigbee.ATTRIBUTE_IAS_ZONE_STATUS && descMap?.value) {
				maps += translateZoneStatus(new ZoneStatus(zigbee.convertToInt(descMap?.value)))
			} else {
				maps += handleAcceleration(descMap)
			}
		}
	} else if (maps[0].name == "temperature") {
		def map = maps[0]
		if (tempOffset) {
			map.value = (int) map.value + (int) tempOffset
		}
		map.descriptionText = temperatureScale == 'C' ? '{{ device.displayName }} was {{ value }}°C' : '{{ device.displayName }} was {{ value }}°F'
		map.translatable = true
	}

	def result = maps.inject([]) {acc, it ->
		if (it) {
			acc << createEvent(it)
		}
	}
	if (description?.startsWith('enroll request')) {
		List cmds = zigbee.enrollResponse()
		log.debug "enroll response: ${cmds}"
		result = cmds?.collect { new physicalgraph.device.HubAction(it) }
	}

	log.info "parse returning $result"
	return result
}

private List<Map> handleAcceleration(descMap) {
	def result = []
	if (descMap.clusterInt == 0xFC02 && descMap.attrInt == 0x0010) {
		///////////////////////////////////////////////////////
		// TODO: append capability event based on motion on/off
		///////////////////////////////////////////////////////
		// Ideas: turn the light on/off based on motion

		def value = descMap.value == "01" ? "started" : "stopped"
		log.debug "Motion has $value"

		result << []

		if (descMap.additionalAttrs) {
			result += parseAxis(descMap.additionalAttrs)
		}
	} else if (descMap.clusterInt == 0xFC02 && descMap.attrInt == 0x0012) {
		def addAttrs = descMap.additionalAttrs ?: []
		addAttrs << ["attrInt": descMap.attrInt, "value": descMap.value]
		result += parseAxis(addAttrs)
	}
	return result
}

private List<Map> parseAxis(List<Map> attrData) {
	def x = hexToSignedInt(attrData.find { it.attrInt == 0x0012 }?.value)
	def y = hexToSignedInt(attrData.find { it.attrInt == 0x0013 }?.value)
	def z = hexToSignedInt(attrData.find { it.attrInt == 0x0014 }?.value)

	if ([x, y ,z].any { it == null }) {
		return []
	}

	///////////////////////////////////////////////////////
	// TODO: return capability event based on acceleration
	///////////////////////////////////////////////////////
	// Ideas:
	//  - Use sensor as a dial to the brightness (level)
	//  - Shake the sensor to increase the brightness
	//  - Turn the brightness up to 100% if the sensor accelerates above a small threshold and
	//    then attempt to carry the sensor across the room without the light coming on
	log.debug "X,Y,Z is $x,$y,$z"

	return []
}

private List<Map> parseIasMessage(String description) {
	ZoneStatus zs = zigbee.parseZoneStatus(description)

	translateZoneStatus(zs)
}

private List<Map> translateZoneStatus(ZoneStatus zs) {
	def value = zs.isAlarm1Set() ? 'open' : 'closed'
	log.debug "Contact: ${device.displayName} value = ${value}"
	def descriptionText = value == 'open' ? '{{ device.displayName }} was opened' : '{{ device.displayName }} was closed'
	[[name: 'contact', value: value, descriptionText: descriptionText, displayed: false, translatable: true]]
}

private Map getBatteryResult(rawValue) {
	log.debug "Battery rawValue = ${rawValue}"

	def result = [:]

	def volts = rawValue / 10

	if (!(rawValue == 0 || rawValue == 255)) {
		result.name = 'battery'
		result.translatable = true
		result.descriptionText = "{{ device.displayName }} battery was {{ value }}%"

		def minVolts = 2.1
		def maxVolts = 2.7

		// Get the current battery percentage as a multiplier 0 - 1
		def curValVolts = Integer.parseInt(device.currentState("battery")?.value ?: "100") / 100.0
		// Find the corresponding voltage from our range
		curValVolts = curValVolts * (maxVolts - minVolts) + minVolts
		// Round to the nearest 10th of a volt
		curValVolts = Math.round(10 * curValVolts) / 10.0
		// Only update the battery reading if we don't have a last reading,
		// OR we have received the same reading twice in a row
		// OR we don't currently have a battery reading
		// OR the value we just received is at least 2 steps off from the last reported value
		// OR the device's firmware is older than 1.15.7
		if(state?.lastVolts == null || state?.lastVolts == volts || device.currentState("battery")?.value == null || Math.abs(curValVolts - volts) > 0.1) {
			def pct = (volts - minVolts) / (maxVolts - minVolts)
			def roundedPct = Math.round(pct * 100)
			if (roundedPct <= 0)
				roundedPct = 1
			result.value = Math.min(100, roundedPct)
		} else {
			// Don't update as we want to smooth the battery values, but do report the last battery state for record keeping purposes
			result.value = device.currentState("battery").value
		}
		state.lastVolts = volts
	}

	return result
}

private Map getBatteryPercentageResult(rawValue) {
	log.debug "Battery Percentage rawValue = ${rawValue} -> ${rawValue / 2}%"
	def result = [:]

	if (0 <= rawValue && rawValue <= 200) {
		result.name = 'battery'
		result.translatable = true
		result.descriptionText = "{{ device.displayName }} battery was {{ value }}%"
		result.value = Math.round(rawValue / 2)
	}

	return result
}

/**
 * PING is used by Device-Watch in attempt to reach the Device
 * */
def ping() {
	zigbee.readAttribute(zigbee.IAS_ZONE_CLUSTER, zigbee.ATTRIBUTE_IAS_ZONE_STATUS)
}

def refresh() {
	log.debug "Refreshing Values "
	def refreshCmds = []

	refreshCmds += zigbee.readAttribute(zigbee.POWER_CONFIGURATION_CLUSTER, 0x0021)
	refreshCmds += zigbee.readAttribute(zigbee.TEMPERATURE_MEASUREMENT_CLUSTER, 0x0000) +
			zigbee.readAttribute(0xFC02, 0x0010, [mfgCode: manufacturerCode]) +
			zigbee.readAttribute(zigbee.IAS_ZONE_CLUSTER, zigbee.ATTRIBUTE_IAS_ZONE_STATUS) +
			zigbee.enrollResponse()

	return refreshCmds
}

def configure() {
	// Device-Watch allows 2 check-in misses from device + ping (plus 1 min lag time)
	// enrolls with default periodic reporting until newer 5 min interval is confirmed
	sendEvent(name: "checkInterval", value: 2 * 60 * 60 + 1 * 60, displayed: false, data: [protocol: "zigbee", hubHardwareId: device.hub.hardwareID, offlinePingable: "1"])

	log.debug "Configuring Reporting"
	def configCmds = [zigbee.readAttribute(zigbee.TEMPERATURE_MEASUREMENT_CLUSTER, 0x0000), zigbee.readAttribute(0xFC02, 0x0010, [mfgCode: manufacturerCode])]
	def batteryAttr = 0x0021
	configCmds += zigbee.readAttribute(zigbee.POWER_CONFIGURATION_CLUSTER, batteryAttr)
	configCmds += zigbee.enrollResponse()

	configCmds += zigbee.writeAttribute(0xFC02, 0x0000, 0x20, 0x14, [mfgCode: manufacturerCode])
	configCmds += zigbee.configureReporting(zigbee.POWER_CONFIGURATION_CLUSTER, 0x0021, DataType.UINT8, 30, 21600, 0x10) +
			zigbee.temperatureConfig(30, 300) +
			zigbee.configureReporting(0xFC02, 0x0010, DataType.BITMAP8, 0, 3600, 0x01, [mfgCode: manufacturerCode]) +
			zigbee.configureReporting(0xFC02, 0x0012, DataType.INT16, 0, 3600, 0x0001, [mfgCode: manufacturerCode]) +
			zigbee.configureReporting(0xFC02, 0x0013, DataType.INT16, 0, 3600, 0x0001, [mfgCode: manufacturerCode]) +
			zigbee.configureReporting(0xFC02, 0x0014, DataType.INT16, 0, 3600, 0x0001, [mfgCode: manufacturerCode])
	configCmds += zigbee.readAttribute(zigbee.POWER_CONFIGURATION_CLUSTER, batteryAttr)
	configCmds += zigbee.readAttribute(zigbee.IAS_ZONE_CLUSTER, zigbee.ATTRIBUTE_IAS_ZONE_STATUS)

	return configCmds
}

def updated() {
	log.debug "updated called"
}

def hexToSignedInt(hexVal) {
	if (!hexVal) {
		return null
	}

	def unsignedVal = new BigInteger(hexVal, 16)
	unsignedVal > 32767 ? unsignedVal - 65536 : unsignedVal
}

private getManufacturerCode() {
	if (device.getDataValue("manufacturer") == "SmartThings") {
		return "0x110A"
	} else if (device.getDataValue("manufacturer") == "Samjin") {
		return "0x1241"
	} else {
		return "0x104E"
	}
}