package com.diozero.api;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.diozero.internal.spi.I2CDeviceInterface;
import com.diozero.util.IOUtil;

public class I2CDevice implements Closeable, I2CConstants {
	private static final Logger logger = LogManager.getLogger(I2CDevice.class);
	
	private I2CDeviceInterface device;
	private int controller;
	private int address;
	private int addressSize;
	private int clockFrequency;

	public I2CDevice(int controller, int address, int addressSize, int clockFrequency) throws IOException {
		device = DeviceFactoryHelper.getNativeDeviceFactory().provisionI2CDevice(controller, address, addressSize, clockFrequency);
		
		this.controller = controller;
		this.address = address;
		this.addressSize = addressSize;
		this.clockFrequency = clockFrequency;
	}

	public int getController() {
		return controller;
	}

	public int getAddress() {
		return address;
	}

	public int getAddressSize() {
		return addressSize;
	}

	public int getClockFrequency() {
		return clockFrequency;
	}

	@Override
	public void close() throws IOException {
		logger.debug("close()");
		device.close();
	}
	
	public final boolean isOpen() {
		return device.isOpen();
	}

	/**
	 * Writes a single byte to a register
	 *
	 * @param register
	 *            Register to write
	 * @param byteToWrite
	 *            Byte to be written
	 */
	public void write(int register, int subAddressSize, byte[] value) throws IOException {
		device.write(register, subAddressSize, ByteBuffer.wrap(value));
	}
	
	public void writeShort(int regAddr, short val) throws IOException {
		ByteBuffer buffer = ByteBuffer.allocateDirect(2);
		buffer.putShort(val);
		buffer.flip();
		device.write(regAddr, SUB_ADDRESS_SIZE_1_BYTE, buffer);
	}

	public void read(int address, int subAddressSize, ByteBuffer buffer) throws IOException {
		device.read(address, subAddressSize, buffer);
	}

	public byte[] read(int address, int subAddressSize, int count) throws IOException {
		ByteBuffer buffer = ByteBuffer.allocateDirect(subAddressSize*count);
		read(address, subAddressSize, buffer);

		// Rewind the byte buffer for reading
		buffer.rewind();

		byte[] data = new byte[count];
		buffer.get(data);

		return data;
	}

	/**
	 * Read single byte from an 8-bit device register.
	 * 
	 * @param regAddr
	 *            Register regAddr to read from
	 */
	public byte readByte(int regAddr) throws IOException {
		// int8_t I2Cdev::readByte(uint8_t devAddr, uint8_t regAddr, uint8_t *data, uint16_t timeout)
		return readByte(regAddr, SUB_ADDRESS_SIZE_1_BYTE);
	}

	public byte readByte(int address, int subAddressSize) throws IOException {
		ByteBuffer buffer = ByteBuffer.allocateDirect(1);
		read(address, subAddressSize, buffer);

		// Rewind the buffer for reading
		buffer.rewind();

		return buffer.get();
	}

	public short readShort(int address, int subAddressSize) throws IOException {
		return readShort(address, subAddressSize);
	}

	public short readShort(int address, int subAddressSize, ByteOrder order) throws IOException {
		ByteBuffer buffer = ByteBuffer.allocateDirect(2);
		read(address, subAddressSize, buffer);

		// Rewind the buffer for reading
		buffer.rewind();

		buffer.order(order);
		return buffer.getShort();
	}

	public int readUShort(int address, int subAddressSize) throws IOException {
		return readUShort(address, subAddressSize, IOUtil.DEFAULT_BYTE_ORDER);
	}

	public int readUShort(int address, int subAddressSize, ByteOrder order) throws IOException {
		return readShort(address, subAddressSize, order) & 0xffff;
	}

	public long readUInt(int address, int subAddressSize, int bytes) throws IOException {
		return readUInt(address, subAddressSize, bytes, IOUtil.DEFAULT_BYTE_ORDER);
	}

	public long readUInt(int address, int subAddressSize, int length, ByteOrder order) throws IOException {
		if (length > 4) {
			throw new IllegalArgumentException("Can't create an int for " + length + " bytes, max length is 4");
		}

		ByteBuffer buffer = ByteBuffer.allocateDirect(length);
		read(address, subAddressSize, buffer);

		// Rewind the buffer for reading
		buffer.rewind();

		return IOUtil.getUInt(buffer, length, order);
	}

	///////////////////////////////////////////////////////////////////////////////////
	// From https://github.com/jrowberg/i2cdevlib/blob/master/Arduino/I2Cdev/I2Cdev.cpp
	///////////////////////////////////////////////////////////////////////////////////

	/**
	 * Read a single bit from an 8-bit device register.
	 * 
	 * @param regAddr
	 *            Register regAddr to read from
	 * @param bitNum
	 *            Bit position to read (0-7)
	 */
	public boolean readBit(int regAddr, int bitNum) throws IOException {
		// int8_t I2Cdev::readBit(uint8_t devAddr, uint8_t regAddr, uint8_t
		// bitNum, uint8_t *data, uint16_t timeout)
		byte b = readByte(regAddr, SUB_ADDRESS_SIZE_1_BYTE);

		return (b & (1 << bitNum)) != 0;
	}

	/**
	 * Read multiple bits from an 8-bit device register.
	 * 
	 * @param regAddr
	 *            Register regAddr to read from
	 * @param bitStart
	 *            First bit position to read (0-7)
	 * @param length
	 *            Number of bits to read (not more than 8)
	 */
	public byte readBits(int regAddr, int bitStart, int length) throws IOException {
		// int8_t I2Cdev::readBits(uint8_t devAddr, uint8_t regAddr, uint8_t
		// bitStart, uint8_t length, uint8_t *data, uint16_t timeout)
		byte b = readByte(regAddr);
		int mask = ((1 << length) - 1) << (bitStart - length + 1);
		b &= mask;
		b >>= (bitStart - length + 1);

		return b;
	}

	/**
	 * Read multiple bytes from an 8-bit device register.
	 * 
	 * @param regAddr
	 *            First register regAddr to read from
	 * @param length
	 *            Number of bytes to read
	 * @param data
	 *            Buffer to store read data in
	 * @param timeout
	 *            Optional read timeout in milliseconds (0 to disable, leave off
	 *            to use default class value in I2Cdev::readTimeout)
	 * @return Number of bytes read (-1 indicates failure)
	 */
	public byte[] readBytes(int regAddr, int length) throws IOException {
		// int8_t I2Cdev::readBytes(uint8_t devAddr, uint8_t regAddr, uint8_t
		// length, uint8_t *data, uint16_t timeout)
		return read(regAddr, SUB_ADDRESS_SIZE_1_BYTE, length);
	}

	/**
	 * write a single bit in an 8-bit device register.
	 * 
	 * @param regAddr
	 *            Register regAddr to write to
	 * @param bitNum
	 *            Bit position to write (0-7)
	 * @param value
	 *            New bit value to write
	 * @return Status of operation (true = success)
	 */
	public void writeBit(int regAddr, int bitNum, int value) throws IOException {
		// bool I2Cdev::writeBit(uint8_t devAddr, uint8_t regAddr, uint8_t
		// bitNum, uint8_t data)
		writeBit(regAddr, bitNum, value != 0);
	}

	/**
	 * write a single bit in an 8-bit device register.
	 * 
	 * @param regAddr
	 *            Register regAddr to write to
	 * @param bitNum
	 *            Bit position to write (0-7)
	 * @param value
	 *            New bit value to write
	 * @return Status of operation (true = success)
	 */
	public void writeBit(int regAddr, int bitNum, boolean value) throws IOException {
		// bool I2Cdev::writeBit(uint8_t devAddr, uint8_t regAddr, uint8_t
		// bitNum, uint8_t data)
		byte b = readByte(regAddr);
		b = (byte)(value ? (b | (1 << bitNum)) : (b & ~(1 << bitNum)));
		writeByte(regAddr, b);
	}

	/**
	 * Write multiple bits in an 8-bit device register.
	 * 
	 * @param regAddr
	 *            Register regAddr to write to
	 * @param bitStart
	 *            First bit position to write (0-7)
	 * @param length
	 *            Number of bits to write (not more than 8)
	 * @param data
	 *            Right-aligned value to write
	 * @return Status of operation (true = success)
	 */
	public void writeBits(int regAddr, int bitStart, int length, int data) throws IOException {
		// bool I2Cdev::writeBits(uint8_t devAddr, uint8_t regAddr, uint8_t
		// bitStart, uint8_t length, uint8_t data)
		// 010 value to write
		// 76543210 bit numbers
		// xxx args: bitStart=4, length=3
		// 00011100 mask byte
		// 10101111 original value (sample)
		// 10100011 original & ~mask
		// 10101011 masked | value
		int b = readByte(regAddr);
		int value = data;
		if (b != 0) {
			int mask = ((1 << length) - 1) << (bitStart - length + 1);
			value <<= (bitStart - length + 1); // shift data into correct
												// position
			value &= mask; // zero all non-important bits in data
			b &= ~(mask); // zero all important bits in existing byte
			b |= value; // combine data with existing byte
			writeByte(regAddr, b);
		}
	}

	/**
	 * Writes a single byte to a register
	 *
	 * @param register
	 *            Register to write
	 * @param byteToWrite
	 *            Byte to be written
	 */
	public void write(int register, int subAddressSize, byte value) throws IOException {
		write(register, subAddressSize, new byte[] { value });
	}

	/**
	 * Write single byte to an 8-bit device register.
	 * 
	 * @param regAddr
	 *            Register address to write to
	 * @param data
	 *            New byte value to write
	 * @return Status of operation (true = success)
	 */
	public void writeByte(int regAddr, int data) throws IOException {
		// bool I2Cdev::writeByte(uint8_t devAddr, uint8_t regAddr, uint8_t
		// data)
		writeByte(regAddr, (byte) data);
	}

	/**
	 * Write single byte to an 8-bit device register.
	 * 
	 * @param regAddr
	 *            Register address to write to
	 * @param data
	 *            New byte value to write
	 * @return Status of operation (true = success)
	 */
	public void writeByte(int regAddr, byte data) throws IOException {
		// bool I2Cdev::writeByte(uint8_t devAddr, uint8_t regAddr, uint8_t
		// data)
		write(regAddr, SUB_ADDRESS_SIZE_1_BYTE, data);
	}

	/**
	 * Write single word to a 16-bit device register.
	 * 
	 * @param regAddr
	 *            Register address to write to
	 * @param data
	 *            New word value to write
	 * @return Status of operation (true = success)
	 */
	public void writeWord(int regAddr, int data) throws IOException {
		// bool I2Cdev::writeWord(uint8_t devAddr, uint8_t regAddr, uint16_t
		// data)
		ByteBuffer buffer = ByteBuffer.allocateDirect(2);
		buffer.putShort((short) data);
		buffer.flip();

		write(regAddr, SUB_ADDRESS_SIZE_2_BYTES, buffer.array());
	}

	/**
	 * Write multiple bytes to an 8-bit device register.
	 * 
	 * @param regAddr
	 *            First register address to write to
	 * @param length
	 *            Number of bytes to write
	 * @param data
	 *            Buffer to copy new data from
	 * @return Status of operation (true = success)
	 */
	public void writeBytes(int regAddr, int length, byte[] data) throws IOException {
		writeBytes(regAddr, length, data, 0);
	}
	
	public void writeBytes(int regAddr, int length, byte[] data, int offset) throws IOException {
		/*
		 * if (I2CDEV_SERIAL_DEBUG) { System.out.format(
		 * "I2C (0x%x) writing %d bytes to 0x%x...%n", devAddr, length,
		 * regAddr); }
		 */

		byte[] dest = new byte[length];
		System.arraycopy(data, offset, dest, 0, length);
		write(regAddr, SUB_ADDRESS_SIZE_1_BYTE, dest);
	}
}
