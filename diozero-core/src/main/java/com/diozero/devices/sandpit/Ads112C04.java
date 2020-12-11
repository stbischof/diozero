package com.diozero.devices.sandpit;

/*-
 * #%L
 * Organisation: diozero
 * Project:      Device I/O Zero - Core
 * Filename:     Ads112C04.java  
 * 
 * This file is part of the diozero project. More information about this project
 * can be found at http://www.diozero.com/
 * %%
 * Copyright (C) 2016 - 2020 diozero
 * %%
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * #L%
 */

import java.io.Closeable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.tinylog.Logger;

import com.diozero.api.I2CDevice;
import com.diozero.util.Crc;
import com.diozero.util.Hex;
import com.diozero.util.PropertyUtil;
import com.diozero.util.SleepUtil;

public class Ads112C04 implements Closeable {
	private static final int NUM_ADC_CHANNELS = 4;

	/**
	 * The ADS112C04 has two address pins: A0 and A1. Each address pin can be tied
	 * to either DGND, DVDD, SDA, or SCL, providing 16 possible unique addresses.
	 * This configuration allows up to 16 different ADS112C04 devices to be present
	 * on the same I2C bus. Name format is A1_A0
	 */
	public enum Address {
		GND_GND(0b01000000), GND_VDD(0b01000001), GND_SDA(0b01000010), GND_SCL(0b01000011), //
		VDD_GND(0b01000100), VDD_VDD(0b01000101), VDD_SDA(0b01000110), VDD_SCL(0b01000111), //
		SDA_GND(0b01001000), SDA_VDD(0b01001001), SDA_SDA(0b01001010), SDA_SCL(0b01001011), //
		SCL_GND(0b01001100), SCL_VDD(0b01001101), SCL_SDA(0b01001110), SCL_SCL(0b01001111);

		private int value;

		private Address(int value) {
			this.value = value;
		}

		public int getValue() {
			return value;
		}
	}

	public enum GainConfig {
		GAIN_1(1, 0b000), GAIN_2(2, 0b001), GAIN_4(4, 0b010), GAIN_8(8, 0b011), GAIN_16(16, 0b100), GAIN_32(32, 0b101),
		GAIN_64(64, 0b110), GAIN_128(128, 0b111);

		private int gain;
		private byte mask;

		private GainConfig(int gain, int mask) {
			this.gain = gain;
			this.mask = (byte) (mask << C0_GAIN_BIT_START);
		}

		public int getGain() {
			return gain;
		}

		byte getMask() {
			return mask;
		}
	}

	public enum PgaBypass {
		ENABLED(0), DISABLED(1);

		private byte mask;

		private PgaBypass(int mask) {
			this.mask = (byte) (mask << C0_PGA_BYPASS_BIT_START);
		}

		byte getMask() {
			return mask;
		}
	}

	public enum DataRate {
		DR_20HZ(20, 0b000), DR_45HZ(45, 0b001), DR_90HZ(90, 0b010), DR_175HZ(175, 0b011), DR_330HZ(330, 0b100),
		DR_600HZ(600, 0b101), DR_1000HZ(1000, 0b110);

		private int dateRate;
		private byte mask;

		private DataRate(int dateRate, int mask) {
			this.dateRate = dateRate;
			this.mask = (byte) (mask << C1_DATA_RATE_BIT_START);
		}

		public int getDataRate() {
			return dateRate;
		}

		byte getMask() {
			return mask;
		}
	}

	public enum OperatingMode {
		NORMAL(1, 0b0), TURBO(2, 0b1);

		private int multiplier;
		private byte mask;

		private OperatingMode(int multiplier, int mask) {
			this.multiplier = multiplier;
			this.mask = (byte) (mask << C1_OP_MODE_BIT_START);
		}

		public int getMultiplier() {
			return multiplier;
		}

		byte getMask() {
			return mask;
		}
	}

	public enum ConversionMode {
		SINGLE_SHOT(0b0), CONTINUOUS(0b1);

		private byte mask;

		private ConversionMode(int mask) {
			this.mask = (byte) (mask << C1_CONV_MODE_BIT_START);
		}

		byte getMask() {
			return mask;
		}
	}

	public enum VRef {
		INTERNAL(0b00), EXTERNAL(0b01), ANALOG_SUPPLY(0b10);

		private byte mask;

		private VRef(int mask) {
			this.mask = (byte) (mask << C1_VREF_BIT_START);
		}

		public int getMask() {
			return mask;
		}
	}

	public enum TemperatureSensorMode {
		ENABLED(true, 0b1), DISABLED(false, 0b0);

		private boolean enabled;
		private byte mask;

		private TemperatureSensorMode(boolean enabled, int mask) {
			this.enabled = enabled;
			this.mask = (byte) (mask << C1_TEMP_SENSOR_BIT_START);
		}

		public boolean isEnabled() {
			return enabled;
		}

		byte getMask() {
			return mask;
		}
	}

	public enum DataCounter {
		ENABLED(true, 0b1), DISABLED(false, 0b0);

		private boolean enabled;
		private byte mask;

		private DataCounter(boolean enabled, int mask) {
			this.enabled = enabled;
			this.mask = (byte) (mask << C2_DATA_CNT_EN_BIT_START);
		}

		public boolean isEnabled() {
			return enabled;
		}

		byte getMask() {
			return mask;
		}
	}

	public enum CrcConfig {
		DISABLED(0b00), INVERTED_DATA_OUTPUT(0b01), CRC16(0b10);

		private byte mask;

		private CrcConfig(int mask) {
			this.mask = (byte) (mask << C2_CRC_EN_BIT_START);
		}

		byte getMask() {
			return mask;
		}
	}

	public enum BurnoutCurrentSources {
		ENABLED(true, 0b1), DISABLED(false, 0b0);

		private boolean enabled;
		private byte mask;

		private BurnoutCurrentSources(boolean enabled, int mask) {
			this.enabled = enabled;
			this.mask = (byte) (mask << C2_BCS_BIT_START);
		}

		public boolean isEnabled() {
			return enabled;
		}

		byte getMask() {
			return mask;
		}
	}

	public enum IdacCurrent {
		IDAC_OFF(0, 0b000), IDAC_10UA(10, 0b001), IDAC_50UA(50, 0b010), IDAC_100UA(100, 0b011), IDAC_250UA(250, 0b100),
		IDAC_500UA(500, 0b101), IDAC_1000UA(1000, 0b110), IDAC_1500UA(1500, 0b111);

		private int microAmps;
		private byte mask;

		private IdacCurrent(int microAmps, int mask) {
			this.microAmps = microAmps;
			this.mask = (byte) (mask << C2_IDAC_CRNT_BIT_START);
		}

		public int getMicroAmps() {
			return microAmps;
		}

		byte getMask() {
			return mask;
		}
	}

	public enum Idac1RoutingConfig {
		DISABLED(0b000), AIN0(0b001), AIN1(0b010), AIN2(0b011), AIN3(0b100), REFP(0b101), REFN(0b110);

		private byte mask;

		private Idac1RoutingConfig(int mask) {
			this.mask = (byte) (mask << C3_I1MUX_BIT_START);
		}

		byte getMask() {
			return mask;
		}
	}

	public enum Idac2RoutingConfig {
		DISABLED(0b000), AIN0(0b001), AIN1(0b010), AIN2(0b011), AIN3(0b100), REFP(0b101), REFN(0b110);

		private byte mask;

		private Idac2RoutingConfig(int mask) {
			this.mask = (byte) (mask << C3_I2MUX_BIT_START);
		}

		byte getMask() {
			return mask;
		}
	}

	/*
	 * The device has four 8-bit configuration registers that are accessible through
	 * the I2C interface using the RREG and WREG commands. After power-up or reset,
	 * all registers are set to the default values (which are all 0). All register
	 * values are retained during power-down mode.
	 */
	private enum ConfigRegister {
		REG0(0b00), REG1(0b01), REG2(0b10), REG3(0b11);

		private byte mask;

		private ConfigRegister(int mask) {
			this.mask = (byte) (mask << 2);
		}

		byte getMask() {
			return mask;
		}
	}

	/*- Config register 0
	 * MUX 7:4 (R/W), Gain 3:1 (R/W), PGA Bypass 0 (R/W)
	 */
	private static final int C0_MUX_BIT_START = 4;
	private static final int C0_GAIN_BIT_START = 1;
	private static final int C0_PGA_BYPASS_BIT_START = 0;
	/*- Config register 1
	 * Data Rate 7:5 (R/W), Operating Mode 4 (R/W), Conversion Mode 3 (R/W),
	 * VRef 2:1 (R/W), Temp. Sensor Mode 0 (R/W)
	 */
	private static final int C1_DATA_RATE_BIT_START = 5;
	private static final int C1_OP_MODE_BIT_START = 4;
	private static final int C1_CONV_MODE_BIT_START = 3;
	private static final int C1_VREF_BIT_START = 1;
	private static final int C1_TEMP_SENSOR_BIT_START = 0;
	/*- Config register 2
	 * Data Ready 7 (R), Data Counter Enable 6 (R/W), CRC Enable 5:4 (R/W),
	 * Burn-out Current Sources 3 (R/W), IDAC Current Setting 0 (R/W)
	 */
	private static final int C2_DATA_RDY_BIT_START = 7;
	private static final int C2_DATA_RDY_MASK = 1 << C2_DATA_RDY_BIT_START;
	private static final int C2_DATA_CNT_EN_BIT_START = 6;
	private static final int C2_CRC_EN_BIT_START = 4;
	private static final int C2_BCS_BIT_START = 3;
	private static final int C2_IDAC_CRNT_BIT_START = 0;
	/*- Config register 3
	 * I1MUX 7:5 (R/W), I2MUX 4:2 (R/W), Reserved 1:0 (R) Always 0
	 */
	private static final int C3_I1MUX_BIT_START = 5;
	private static final int C3_I2MUX_BIT_START = 2;

	private static final byte COMMAND_RESET = (byte) 0b00000110;
	private static final byte COMMAND_START = (byte) 0b00001000;
	private static final byte COMMAND_POWER_DOWN = (byte) 0b00000010;
	private static final byte COMMAND_RDATA = (byte) 0b00010000;
	private static final byte COMMAND_READ_REG = (byte) 0b00100000;
	private static final byte COMMAND_WRITE_REG = (byte) 0b01000000;

	// The CRC is based on the CRC-16-CCITT polynomial: x16 + x12 + x5 + 1 with an
	// initial value of FFFFh.
	private static final Crc.Params CRC_PARAMS = new Crc.Params(0b10001000000100001, 0xffff, false, false, 0x0000);

	public static class Builder {
		private int controller;
		private Address address;
		private GainConfig gainConfig = GainConfig.GAIN_1;
		private PgaBypass pgaBypass = PgaBypass.ENABLED;
		private DataRate dataRate = DataRate.DR_20HZ;
		private OperatingMode operatingMode = OperatingMode.NORMAL;
		private VRef vRef = VRef.INTERNAL;
		private TemperatureSensorMode tsMode = TemperatureSensorMode.DISABLED;
		private DataCounter dataCounter = DataCounter.DISABLED;
		private CrcConfig crcConfig = CrcConfig.DISABLED;
		private BurnoutCurrentSources burnoutCurrentSources = BurnoutCurrentSources.DISABLED;
		private IdacCurrent idacCurrent = IdacCurrent.IDAC_OFF;
		private Idac1RoutingConfig idac1RoutingConfig = Idac1RoutingConfig.DISABLED;
		private Idac2RoutingConfig idac2RoutingConfig = Idac2RoutingConfig.DISABLED;

		protected Builder(Address address) {
			this.address = address;
		}

		public Builder setController(int controller) {
			this.controller = controller;
			return this;
		}

		public Builder setGainConfig(GainConfig gainConfig) {
			this.gainConfig = gainConfig;
			return this;
		}

		public Builder setPgaBypass(PgaBypass pgaBypass) {
			this.pgaBypass = pgaBypass;
			return this;
		}

		public Builder setPgaBypassEnabled(boolean pgaBypassEnabled) {
			this.pgaBypass = pgaBypassEnabled ? PgaBypass.ENABLED : PgaBypass.DISABLED;
			return this;
		}

		public Builder setDataRate(DataRate dataRate) {
			this.dataRate = dataRate;
			return this;
		}

		public Builder setOperatingMode(OperatingMode operatingMode) {
			this.operatingMode = operatingMode;
			return this;
		}

		public Builder setTurboModeEnabled(boolean turboModeEnabled) {
			this.operatingMode = turboModeEnabled ? OperatingMode.TURBO : OperatingMode.NORMAL;
			return this;
		}

		public Builder setVRef(VRef vRef) {
			this.vRef = vRef;
			return this;
		}

		public Builder setTemperatureSensorMode(TemperatureSensorMode tsMode) {
			this.tsMode = tsMode;
			return this;
		}

		public Builder setTemperatureSensorEnabled(boolean tsEnabled) {
			this.tsMode = tsEnabled ? TemperatureSensorMode.ENABLED : TemperatureSensorMode.DISABLED;
			return this;
		}

		public Builder setDataCounter(DataCounter dataCounter) {
			this.dataCounter = dataCounter;
			return this;
		}

		public Builder setDataCounterEnabled(boolean dcEnabled) {
			this.dataCounter = dcEnabled ? DataCounter.ENABLED : DataCounter.DISABLED;
			return this;
		}

		public Builder setCrcConfig(CrcConfig crcConfig) {
			this.crcConfig = crcConfig;
			return this;
		}

		public Builder setBurnoutCurrentSources(BurnoutCurrentSources burnoutCurrentSources) {
			this.burnoutCurrentSources = burnoutCurrentSources;
			return this;
		}

		public Builder setBurnoutCurrentSourcesEnabled(boolean burnoutCurrentSourcesEnabled) {
			this.burnoutCurrentSources = burnoutCurrentSourcesEnabled ? BurnoutCurrentSources.ENABLED
					: BurnoutCurrentSources.DISABLED;
			return this;
		}

		public Builder setIdacCurrent(IdacCurrent idacCurrent) {
			this.idacCurrent = idacCurrent;
			return this;
		}

		public Builder setIdac1RoutingConfig(Idac1RoutingConfig idac1RoutingConfig) {
			this.idac1RoutingConfig = idac1RoutingConfig;
			return this;
		}

		public Builder setIdac2RoutingConfig(Idac2RoutingConfig idac2RoutingConfig) {
			this.idac2RoutingConfig = idac2RoutingConfig;
			return this;
		}

		public Ads112C04 build() {
			return new Ads112C04(controller, address, gainConfig, pgaBypass, dataRate, operatingMode, vRef, tsMode,
					dataCounter, crcConfig, burnoutCurrentSources, idacCurrent, idac1RoutingConfig, idac2RoutingConfig);
		}
	}

	public static Builder builder(Address address) {
		return new Builder(address);
	}

	private I2CDevice device;
	private GainConfig gainConfig;
	private PgaBypass pgaBypass;
	private DataRate dataRate;
	private OperatingMode operatingMode;
	private ConversionMode conversionMode;
	private VRef vRef;
	private TemperatureSensorMode tsMode;
	private DataCounter dataCounter;
	private CrcConfig crcConfig;
	private BurnoutCurrentSources burnoutCurrentSources;
	private IdacCurrent idacCurrent;
	private Idac1RoutingConfig idac1RoutingConfig;
	private Idac2RoutingConfig idac2RoutingConfig;
	private byte inputMultiplexer;
	private int lastDataCounter;
	private boolean repeatedStart;

	protected Ads112C04(int controller, Address address, GainConfig gainConfig, PgaBypass pgaBypass, DataRate dataRate,
			OperatingMode operatingMode, VRef vRef, TemperatureSensorMode tsMode, DataCounter dataCounter,
			CrcConfig crcConfig, BurnoutCurrentSources burnoutCurrentSources, IdacCurrent idacCurrent,
			Idac1RoutingConfig idac1RoutingConfig, Idac2RoutingConfig idac2RoutingConfig) {
		this.gainConfig = gainConfig;
		this.pgaBypass = pgaBypass;
		this.dataRate = dataRate;
		this.operatingMode = operatingMode;
		this.vRef = vRef;
		this.tsMode = tsMode;
		this.dataCounter = dataCounter;
		this.crcConfig = crcConfig;
		this.burnoutCurrentSources = burnoutCurrentSources;
		this.idacCurrent = idacCurrent;
		this.idac1RoutingConfig = idac1RoutingConfig;
		this.idac2RoutingConfig = idac2RoutingConfig;

		inputMultiplexer = (byte) ((0b1000) << C0_MUX_BIT_START);

		conversionMode = ConversionMode.SINGLE_SHOT;
		lastDataCounter = -1;
		repeatedStart = PropertyUtil.getBooleanProperty("diozero.i2c.repeatedStart", true);
		Logger.debug("repeatedStart: {}", Boolean.valueOf(repeatedStart));

		device = I2CDevice.builder(address.getValue()).setController(controller).setByteOrder(ByteOrder.BIG_ENDIAN)
				.build();

		reset();

		setConfig0();
		setConfig1();
		setConfig2();
		setConfig3();
	}

	@Override
	public void close() {
		device.close();
	}

	public void reset() {
		Logger.debug("reset");
		device.writeByte(COMMAND_RESET);
		// TODO Check delays
		SleepUtil.sleepMillis(1);
	}

	public void start() {
		Logger.debug("start");
		device.writeByte(COMMAND_START);
		// TODO Check delays
		SleepUtil.busySleep(10_000);
	}

	public void powerDown() {
		Logger.debug("powerDown");
		device.writeByte(COMMAND_POWER_DOWN);
		// TODO Check delays
		SleepUtil.sleepMillis(1);
	}

	private byte readConfigRegister(ConfigRegister register) {
		// TODO Also read CRC data if enabled!
		if (crcConfig == CrcConfig.DISABLED) {
			return device.readByteData(COMMAND_READ_REG | register.getMask());
		}

		byte[] buffer = new byte[crcConfig == CrcConfig.CRC16 ? 3 : 2];
		device.readI2CBlockData(COMMAND_READ_REG, buffer);
		if (Logger.isTraceEnabled()) {
			Hex.dumpByteArray(buffer);
		}
		if (crcConfig == CrcConfig.CRC16) {
			int calc_crc = Crc.crc16(CRC_PARAMS, buffer[0]);
			int crc = (buffer[1] & 0xff) << 8 | (buffer[2] & 0xff);
			if (crc != calc_crc) {
				Logger.warn("CRC-16 error calculated {}, got {} for value {}", Integer.valueOf(calc_crc),
						Integer.valueOf(crc), Byte.valueOf(buffer[0]));
			}
		} else {
			byte calc_val_inverted = (byte) (~buffer[0]);
			if (buffer[1] != calc_val_inverted) {
				Logger.warn("Data Integrity error calculated {}, got {} for value {}",
						Integer.valueOf(calc_val_inverted), Integer.valueOf(buffer[1]), Byte.valueOf(buffer[0]));
			}
		}

		return buffer[0];
	}

	private void writeConfigRegister(ConfigRegister register, byte value) {
		device.writeByteData(COMMAND_WRITE_REG | register.getMask(), value);
		// TODO Check delays
	}

	private void setConfig0() {
		// Logger.debug("setConfig0");
		writeConfigRegister(ConfigRegister.REG0,
				(byte) (inputMultiplexer | gainConfig.getMask() | pgaBypass.getMask()));
	}

	private void setConfig1() {
		// Logger.debug("setConfig1");
		writeConfigRegister(ConfigRegister.REG1, (byte) (dataRate.getMask() | operatingMode.getMask()
				| conversionMode.getMask() | vRef.getMask() | tsMode.getMask()));
	}

	private void setConfig2() {
		// Logger.debug("setConfig2");
		writeConfigRegister(ConfigRegister.REG2, (byte) (dataCounter.getMask() | crcConfig.getMask()
				| burnoutCurrentSources.getMask() | idacCurrent.getMask()));
	}

	private void setConfig3() {
		// Logger.debug("setConfig3");
		writeConfigRegister(ConfigRegister.REG3, (byte) (idac1RoutingConfig.getMask() | idac2RoutingConfig.getMask()));
	}

	public int getInputMultiplexer() {
		return inputMultiplexer;
	}

	/**
	 * Set the input multiplexer configuration
	 * 
	 * For settings where AINN = AVSS, the PGA must be disabled (PGA_BYPASS = 1) and
	 * only gains 1, 2, and 4 can be used.
	 * 
	 * <pre>
	 * 0000 : AINP = AIN0, AINN = AIN1 (default)
	 * 0001 : AINP = AIN0, AINN = AIN2
	 * 0010 : AINP = AIN0, AINN = AIN3
	 * 0011 : AINP = AIN1, AINN = AIN0
	 * 0100 : AINP = AIN1, AINN = AIN2
	 * 0101 : AINP = AIN1, AINN = AIN3
	 * 0110 : AINP = AIN2, AINN = AIN3
	 * 0111 : AINP = AIN3, AINN = AIN2
	 * 1000 : AINP = AIN0, AINN = AVSS
	 * 1001 : AINP = AIN1, AINN = AVSS
	 * 1010 : AINP = AIN2, AINN = AVSS
	 * 1011 : AINP = AIN3, AINN = AVSS
	 * 1100 : (V(REFP) – V(REFN)) / 4 monitor (PGA bypassed)
	 * 1101 : (AVDD – AVSS) / 4 monitor (PGA bypassed)
	 * 1110 : AINP and AINN shorted to (AVDD + AVSS) / 2
	 * </pre>
	 * 
	 * @param inputMultiplexer the input multiplexer
	 */
	public void setInputMultiplexer(int inputMultiplexer) {
		if (inputMultiplexer < 0 || inputMultiplexer > 0b1110) {
			throw new IllegalArgumentException(
					"Invalid input multiplexer value: " + inputMultiplexer + " must be 0..14");
		}

		this.inputMultiplexer = (byte) (inputMultiplexer & 0xf);
		setConfig0();
	}

	public GainConfig getGainConfig() {
		return gainConfig;
	}

	public void setGainConfig(GainConfig gainConfig) {
		this.gainConfig = gainConfig;
		setConfig0();
	}

	public PgaBypass getPgaBypass() {
		return pgaBypass;
	}

	public void setPgaBypass(PgaBypass pgaBypass) {
		this.pgaBypass = pgaBypass;
		setConfig0();
	}

	public DataRate getDataRate() {
		return dataRate;
	}

	public void setDataRate(DataRate dataRate) {
		this.dataRate = dataRate;
		setConfig1();
	}

	public boolean isTurboModeEnabled() {
		return operatingMode == OperatingMode.TURBO;
	}

	public void setTurboModeEnabled(boolean enabled) {
		this.operatingMode = enabled ? OperatingMode.TURBO : OperatingMode.NORMAL;
		setConfig1();
	}

	public int getDataRateFrequency() {
		return dataRate.getDataRate() * operatingMode.getMultiplier();
	}

	public VRef getVRef() {
		return vRef;
	}

	public void setVRef(VRef vRef) {
		this.vRef = vRef;
		setConfig1();
	}

	public boolean isTemperatureSensorModeEnabled() {
		return tsMode.isEnabled();
	}

	public void setTemperatureSensorModeEnabled(boolean enabled) {
		this.tsMode = enabled ? TemperatureSensorMode.ENABLED : TemperatureSensorMode.DISABLED;
		setConfig1();
	}

	public boolean isDataCounterEnabled() {
		return dataCounter.isEnabled();
	}

	public void setDataCounterEnabled(boolean enabled) {
		this.dataCounter = enabled ? DataCounter.ENABLED : DataCounter.DISABLED;
		setConfig2();
	}

	public CrcConfig getCrcConfig() {
		return crcConfig;
	}

	public void setCrcConfig(CrcConfig crcConfig) {
		this.crcConfig = crcConfig;
		setConfig2();
	}

	public BurnoutCurrentSources getBurnoutCurrentSources() {
		return burnoutCurrentSources;
	}

	public void setBurnoutCurrentSources(BurnoutCurrentSources burnoutCurrentSources) {
		this.burnoutCurrentSources = burnoutCurrentSources;
		setConfig2();
	}

	public IdacCurrent getIdacCurrent() {
		return idacCurrent;
	}

	public void setIdacCurrent(IdacCurrent idacCurrent) {
		this.idacCurrent = idacCurrent;
		setConfig2();
	}

	public Idac1RoutingConfig getIdac1RoutingConfig() {
		return idac1RoutingConfig;
	}

	public void setIdac1RoutingConfig(Idac1RoutingConfig idac1RoutingConfig) {
		this.idac1RoutingConfig = idac1RoutingConfig;
		setConfig3();
	}

	public Idac2RoutingConfig getIdac2RoutingConfig() {
		return idac2RoutingConfig;
	}

	public void setIdac2RoutingConfig(Idac2RoutingConfig idac2RoutingConfig) {
		this.idac2RoutingConfig = idac2RoutingConfig;
		setConfig3();
	}

	public void setSingleShotMode() {
		// System.out.println("getValueSingle");
		conversionMode = ConversionMode.SINGLE_SHOT;
		setConfig1();

		// Start command must be issued each time the CM bit is changed
		start();
	}

	public short getSingleShotReading(int adcNumber) {
		if (adcNumber < 0 || adcNumber >= NUM_ADC_CHANNELS) {
			throw new IllegalArgumentException("Invalid input channel number - " + adcNumber);
		}

		inputMultiplexer = (byte) ((0b1000 + adcNumber) << C0_MUX_BIT_START);
		setConfig0();

		// Must issue a start command to trigger a new reading
		start();

		return getReadingOnDataReadyBit();
	}

	public void setContinuousMode(int adcNumber) {
		inputMultiplexer = (byte) ((0b1000 + adcNumber) << C0_MUX_BIT_START);
		setConfig0();
		conversionMode = ConversionMode.CONTINUOUS;
		setConfig1();

		start();
	}

	/**
	 * Read data whenever the data read bit is set in Config Register #2
	 * 
	 * @return the raw analog data reading in signed short format
	 */
	public short getReadingOnDataReadyBit() {
		/*-
		 * DC enabled and CRC disabled is 3 bytes (1 DC, 2 data)
		 * DC enabled and CRC enabled is 5 bytes (1 DC, 2 data, 2 CRC). 
		 * DC disabled and CRC enabled is 4 bytes (2 data, 2 CRC). 
		 * DC enabled and CRC inverted is 6 bytes (1 DC, 2 data, 1 DC inv. and 2 data inv.).
		 * DC disabled and CRC inverted is 4 bytes (2 data, 2 data inv.).
		 */
		int bytes_to_read = 2;
		if (dataCounter.isEnabled()) {
			bytes_to_read++;
		}
		if (crcConfig != CrcConfig.DISABLED) {
			bytes_to_read += 2;
			if (dataCounter.isEnabled() && crcConfig == CrcConfig.INVERTED_DATA_OUTPUT) {
				bytes_to_read++;
			}
		}

		// Logger.debug("Waiting for data to be available...");
		// Wait for the Data Ready bit to be set in config register #2
		while (true) {
			if ((readConfigRegister(ConfigRegister.REG2) & C2_DATA_RDY_MASK) != 0) {
				break;
			}
			// 100 nS
			SleepUtil.busySleep(100);
		}
		// Logger.debug("Data available");

		// SleepUtil.sleepMillis(2);

		/*-
		byte[] buffer = device.readI2CBlockDataByteArray(COMMAND_RDATA, bytes_to_read);
		Logger.debug("Read {} bytes:", Integer.valueOf(buffer.length));
		*/
		byte[] buffer = new byte[bytes_to_read];
		device.readNoStop(COMMAND_RDATA, bytes_to_read, buffer, repeatedStart);
		if (Logger.isTraceEnabled()) {
			Hex.dumpByteArray(buffer);
		}

		ByteBuffer bb = ByteBuffer.wrap(buffer);
		bb.order(ByteOrder.BIG_ENDIAN);
		int counter = -1;
		if (dataCounter.isEnabled()) {
			counter = bb.get() & 0xff;
			Logger.debug("Conversion counter: {}", Integer.valueOf(counter));
		}

		short value = bb.getShort();
		if (crcConfig != CrcConfig.DISABLED) {
			// Validate the CRC value
			if (crcConfig == CrcConfig.INVERTED_DATA_OUTPUT) {
				// A bitwise-inverted version of the data
				if (dataCounter.isEnabled()) {
					int counter_inverted = bb.get() & 0xff;
					int calc_counter_inverted = ~counter & 0xff;
					if (calc_counter_inverted != counter_inverted) {
						Logger.warn("Inversion error for counter {}, calculated {}, got {}", Integer.valueOf(counter),
								Integer.valueOf(calc_counter_inverted), Integer.valueOf(counter_inverted));
					}
				}
				short value_inverted = bb.getShort();
				short calc_val_inverted = (short) (~value);
				if (calc_val_inverted != value_inverted) {
					Logger.warn("Inversion error for data {}, calculated {}, got {}. DC Enabled: {}{}",
							Short.valueOf(value), Short.valueOf(calc_val_inverted), Short.valueOf(value_inverted),
							Boolean.valueOf(dataCounter.isEnabled()), dataCounter.isEnabled() ? " - " + counter : "");
				}
			} else if (crcConfig == CrcConfig.CRC16) {
				int crc_val = bb.getShort() & 0xffff;
				// In CRC mode, the checksum bytes are the 16-bit remainder of the bitwise
				// exclusive-OR (XOR) of the data bytes with a CRC polynomial
				/*-
				 * The CRC is "for the entire data being returned"
				 * i.e. includes the data counter if present
				 * https://e2e.ti.com/support/data-converters/f/73/t/758829
				 * From the datasheet:
				 * The optional data counter word that precedes conversion data is covered by both data
				 * integrity options.
				 */
				int calc_crc_val;
				if (dataCounter.isEnabled()) {
					calc_crc_val = Crc.crc16(CRC_PARAMS, (byte) counter, (byte) (value >> 8), (byte) value);
				} else {
					calc_crc_val = Crc.crc16Short(CRC_PARAMS, value);
				}
				if (calc_crc_val != crc_val) {
					Logger.warn("CRC error for value {}, calculated {}, got {}. DC Enabled: {}{}", Short.valueOf(value),
							Integer.valueOf((calc_crc_val)), Integer.valueOf(crc_val),
							Boolean.valueOf(dataCounter.isEnabled()), dataCounter.isEnabled() ? " - " + counter : "");
				}
			}
		}

		return value;
	}

	public short getReadingOnDataCounterChange() {
		// Data counter must be available for this method to work
		if (dataCounter == DataCounter.DISABLED) {
			throw new IllegalArgumentException("Data counter must be enabled");
		}

		byte[] buffer;
		switch (crcConfig) {
		case CRC16:
			buffer = new byte[5];
			break;
		case INVERTED_DATA_OUTPUT:
			buffer = new byte[6];
			break;
		case DISABLED:
		default:
			buffer = new byte[3];
		}

		short value;
		while (true) {
			device.readI2CBlockData(COMMAND_RDATA, buffer);
			int new_dc = buffer[0] & 0xff;
			if (new_dc != lastDataCounter) {
				lastDataCounter = new_dc;
				value = (short) ((buffer[1] << 8) | (buffer[2] & 0xff));
				// TODO Validate the CRC
				break;
			}
			SleepUtil.busySleep(100);
		}

		return value;
	}

	public static void main(String[] args) {
		int controller = 1;
		if (args.length > 0) {
			controller = Integer.parseInt(args[0]);
		}

		try (Ads112C04 ads = Ads112C04.builder(Address.GND_GND) //
				.setController(controller) //
				.setCrcConfig(CrcConfig.DISABLED) //
				.setDataCounterEnabled(false) //
				.setDataRate(DataRate.DR_20HZ) //
				.setGainConfig(GainConfig.GAIN_1) //
				.setPgaBypassEnabled(false) //
				.setTurboModeEnabled(false) //
				.setVRef(VRef.ANALOG_SUPPLY) //
				.build()) {
			// First do some single shot tests
			ads.setSingleShotMode();

			ads.crcConfig = CrcConfig.INVERTED_DATA_OUTPUT;
			ads.dataCounter = DataCounter.ENABLED;
			ads.setConfig2();
			for (int i = 0; i < 5; i++) {
				short reading = ads.getSingleShotReading(0);
				Logger.info("Single-shot reading: {}. CRC Config: {}, DC: {}", Short.valueOf(reading), ads.crcConfig,
						ads.dataCounter);
			}

			ads.crcConfig = CrcConfig.INVERTED_DATA_OUTPUT;
			ads.dataCounter = DataCounter.DISABLED;
			ads.setConfig2();
			for (int i = 0; i < 5; i++) {
				short reading = ads.getSingleShotReading(0);
				Logger.info("Single-shot reading: {}. CRC Config: {}, DC: {}", Short.valueOf(reading), ads.crcConfig,
						ads.dataCounter);
			}

			ads.crcConfig = CrcConfig.CRC16;
			ads.dataCounter = DataCounter.ENABLED;
			ads.setConfig2();
			for (int i = 0; i < 5; i++) {
				short reading = ads.getSingleShotReading(0);
				Logger.info("Single-shot reading: {}. CRC Config: {}, DC: {}", Short.valueOf(reading), ads.crcConfig,
						ads.dataCounter);
			}

			ads.crcConfig = CrcConfig.CRC16;
			ads.dataCounter = DataCounter.DISABLED;
			ads.setConfig2();
			for (int i = 0; i < 5; i++) {
				short reading = ads.getSingleShotReading(0);
				Logger.info("Single-shot reading: {}. CRC Config: {}, DC: {}", Short.valueOf(reading), ads.crcConfig,
						ads.dataCounter);
			}

			ads.crcConfig = CrcConfig.DISABLED;
			ads.dataCounter = DataCounter.ENABLED;
			ads.setConfig2();
			for (int i = 0; i < 5; i++) {
				short reading = ads.getSingleShotReading(0);
				Logger.info("Single-shot reading: {}. CRC Config: {}, DC: {}", Short.valueOf(reading), ads.crcConfig,
						ads.dataCounter);
			}

			ads.crcConfig = CrcConfig.DISABLED;
			ads.dataCounter = DataCounter.DISABLED;
			ads.setConfig2();
			for (int i = 0; i < 5; i++) {
				short reading = ads.getSingleShotReading(0);
				Logger.info("Single-shot reading: {}. CRC Config: {}, DC: {}", Short.valueOf(reading), ads.crcConfig,
						ads.dataCounter);
			}

			// Then do continuous mode tests, check if observed frequency correlates with
			// that configured
			ads.crcConfig = CrcConfig.DISABLED;
			ads.dataCounter = DataCounter.ENABLED;
			ads.setConfig2();
			ads.dataRate = DataRate.DR_1000HZ;
			ads.operatingMode = OperatingMode.TURBO;
			ads.setContinuousMode(0);

			int readings = 10_000;
			if (args.length > 1) {
				readings = Integer.parseInt(args[1]);
			}
			Logger.info("Starting readings with a data rate of {} SPS...",
					Integer.valueOf(ads.dataRate.getDataRate() * ads.operatingMode.getMultiplier()));
			float avg = 0;
			long start_ms = System.currentTimeMillis();
			for (int i = 1; i <= readings; i++) {
				avg += ((ads.getReadingOnDataCounterChange() - avg) / i);
			}
			long duration_ms = System.currentTimeMillis() - start_ms;
			double frequency = readings / (duration_ms / 1000.0);

			Logger.info("Average Value: {#,###.0}, # readings: {#,###}, duration: {#,###.#} ms, frequency: {#,###} Hz",
					Float.valueOf(avg), Integer.valueOf(readings), Long.valueOf(duration_ms),
					Double.valueOf(frequency));

			// Switch back to single shot mode
			short reading = ads.getSingleShotReading(0);
			Logger.info("Single-shot reading prior to power-down: {}", Short.valueOf(reading));

			// Finally power-down the ADS
			ads.powerDown();
		}
	}
}
