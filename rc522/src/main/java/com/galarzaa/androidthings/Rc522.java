package com.galarzaa.androidthings;

import android.content.Context;

import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.SpiDevice;

import java.io.IOException;

/**
 * Based on pi-rc22 by ondryaso
 * https://github.com/ondryaso/pi-rc522/
 *
 * MFRC522 Reference
 * https://www.nxp.com/documents/data_sheet/MFRC522.pdf
 * @author Allan Galarza
 */

public class Rc522 {
    private SpiDevice device;
    private Gpio resetPin;
    private int busSpeed = 1000000;

    private byte[] uid;

    private byte[] backData;
    private int backLength;

    private static final byte MAX_LENGTH = 16;

    /* Found in Table 149, page 70*/
    private static final byte COMMAND_IDLE = 0x00;
    private static final byte COMMAND_CALCULATE_CRC = 0x03;
    private static final byte COMMAND_TRANSMIT = 0x04;
    private static final byte COMMAND_RECEIVE = 0x08;
    private static final byte COMMAND_TRANSCEIVE = 0x0C;
    private static final byte COMMAND_MF_AUTHENT = 0x0E;
    private static final byte COMMAND_SOFT_RESET = 0x0F;

    public static final byte AUTH_A = 0x60;
    public static final byte AUTH_B = 0x61;

    private static final byte COMMAND_READ = 0x30;
    private static final byte COMMAND_WRITE = (byte) 0xA0;
    private static final byte COMMAND_INCREMENT = (byte) 0xC1;
    private static final byte COMMAND_DECREMENT = (byte) 0xC0;
    private static final byte COMMAND_RESTORE = (byte) 0xC2;
    private static final byte COMMAND_TANSFER = (byte) 0xb0;

    private static final byte COMMAND_REQUIRE_ID = 0x26;
    private static final byte COMMAND_REQUIRE_ALL = 0x52;
    private static final byte COMMAND_ANTICOLLISION = (byte) 0x93;
    private static final byte COMMAND_SELECT = (byte) 0x93;
    private static final byte COMMAND_END = 0x50;

    /* Found in table 20, page 36 */
    private static final byte REGISTER_COMMAND = 0x01; //CommandReg
    private static final byte REGISTER_INTERRUPT_ENABLE = 0x02; //ComIEnReg
    private static final byte REGISTER_COM_IRQ = 0x04; // DivIEnReg
    private static final byte REGISTER_DIV_IRQ = 0x05; //ComIrqReg
    private static final byte REGISTER_ERROR = 0x06; //ErrorReg
    private static final byte REGISTER_COMMUNICATION_STATUS = 0x07; //Status1Reg
    private static final byte REGISTER_RXTX_STATUS = 0x08; //Status2Reg
    private static final byte REGISTER_FIFO_DATA = 0x09; //FIFODataReg
    private static final byte REGISTER_FIFO_LEVEL = 0x0A; //FIFOLevelReg
    private static final byte REGISTER_CONTROL = 0x0C; //ControlReg
    private static final byte REGISTER_BIT_FRAMING = 0x0D; //BitFramingReg
    private static final byte REGISTER_MODE = 0x11; //ModeReg
    private static final byte REGISTER_TX_CONTROL = 0x14; //TxControlReg
    private static final byte REGISTER_TX_MODE = 0x15; //TxASKReg
    private static final byte REGISTER_CRC_RESULT_HIGH = 0x21; //CRCResultReg
    private static final byte REGISTER_CRC_RESULT_LOW = 0x22; //CRCResultReg
    private static final byte REGISTER_TIMER_MODE = 0x2A; //TModeReg
    private static final byte REGISTER_TIMER_PRESCALER_MODE = 0x2B; //TPrescalerReg
    private static final byte REGISTER_TIMER_RELOAD_HIGH = 0x2C; //TReloadReg
    private static final byte REGISTER_TIMER_RELOAD_LOW = 0x2D; //TReloadReg
    private boolean authenticated;

    /**
     * Initializes RC522 with the configured SPI port and pins.
     * @param context Parameter no longer used, use {@link #Rc522(SpiDevice, Gpio)} instead.
     * @param spiDevice SPI port used on the board
     * @param resetPin Pin connected to the RST pin on the RC522
     * @deprecated use {@link #Rc522(SpiDevice, Gpio)} instead.
     */
    @Deprecated
    public Rc522(Context context, SpiDevice spiDevice, Gpio resetPin) throws IOException {
        this.device = spiDevice;
        this.resetPin = resetPin;
        initializePeripherals();
    }

    /**
     * Initializes RC522 with the configured SPI port and pins.
     * @param spiDevice SPI port used on the board
     * @param resetPin Pin connected to the RST pin on the RC522
     */
    public Rc522(SpiDevice spiDevice, Gpio resetPin) throws IOException {
        this.device = spiDevice;
        this.resetPin = resetPin;
        initializePeripherals();
    }

    /**
     *  Performs the initial configuration on hardware ports
     * @throws IOException if the hardware board had a problem with its hardware ports
     */
    private void initializePeripherals() throws IOException {
        device.setFrequency(busSpeed);
        resetPin.setDirection(Gpio.DIRECTION_OUT_INITIALLY_HIGH);
        initializeDevice();
    }

    /**
     * Performs the inital device setup and configure the pins used
     */
    private void initializeDevice(){
        reset();
        writeRegister(REGISTER_TIMER_MODE, (byte) 0x8D);
        writeRegister(REGISTER_TIMER_PRESCALER_MODE, (byte) 0x3E);
        writeRegister(REGISTER_TIMER_RELOAD_LOW, (byte) 30);
        writeRegister(REGISTER_TIMER_RELOAD_HIGH, (byte) 0);
        writeRegister(REGISTER_TX_MODE, (byte) 0x40);
        writeRegister(REGISTER_MODE, (byte) 0x3D);
        setAntenna(true);
    }

    /**
     * Gets the UID of the last card that was successfully read. This may be empty if no hard has
     * been read before.
     * @return A byte array containing the card's UID.
     */
    public byte[] getUid(){
        return uid;
    }

    /**
     * @deprecated Method renamed, use {@link #getUid()} instead
     */
    @Deprecated
    public byte[] getUuid(){
        return getUid();
    }

    /**
     * Performs a soft reset on the Rc522
     */
    private void reset(){
        writeRegister(REGISTER_COMMAND, COMMAND_SOFT_RESET);
    }

    /**
     * Writes to a RC522 register
     * @param address The address to write to
     * @param value The value that will be written
     */
    private void writeRegister(byte address, byte value){
        byte buffer[] = {(byte) (((address << 1) & 0x7E)), value};
        byte response[] = new byte[buffer.length];
        try {
            device.transfer(buffer, response, buffer.length);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Reads the current value on the RC522's register
     * @param address The address to read from
     * @return the byte value currently stored in the register
     */
    private byte readRegister(byte address){
        byte buffer[] = {(byte) (((address << 1) & 0x7E) | 0x80), 0};
        byte response[] = new byte[buffer.length];
        try {
            device.transfer(buffer, response, buffer.length);
            return response[1];
        } catch (IOException e) {
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * Disables or enables the RC522's antenna
     * @param enabled State to set the antenna to
     */
    private void setAntenna(boolean enabled){
        if(enabled){
            byte currentState = readRegister(REGISTER_TX_CONTROL);
            if((currentState & 0x03) != 0x03){
                setBitMask(REGISTER_TX_CONTROL, (byte) 0x03);
            }
        }else{
            clearBitMask(REGISTER_TX_CONTROL, (byte) 0x03);
        }
    }

    /**
     * Sets the bits of a register according to a bit mask
     * This allows turning on only specific bytes of a register without altering the rest
     * @param address The register's address
     * @param mask The mask to apply
     */
    private void setBitMask(byte address, byte mask){
        byte value = readRegister(address);
        writeRegister(address, (byte) (value | mask));
    }

    /**
     * Clears the bits of a register according to a bit mask
     * This allows turning off only specific bytes of a register without altering the rest
     * @param address The register's address
     * @param mask The mask to apply
     */
    private void clearBitMask(byte address, byte mask){
        byte value = readRegister(address);
        writeRegister(address, (byte) (value & (~mask)));
    }

    private boolean writeCard(byte command, byte [] data){
        backData = new byte[16];
        backLength = 0;
        byte irq = 0;
        byte irqWait = 0;
        byte lastBits = 0;
        boolean success = true;
        if(command == COMMAND_MF_AUTHENT){
            irq = 0x12;
            irqWait = 0x10;
        }
        if(command == COMMAND_TRANSCEIVE){
            irq = 0x77;
            irqWait = 0x30;
        }
        writeRegister(REGISTER_INTERRUPT_ENABLE, (byte) (irq | 0x80));
        clearBitMask(REGISTER_COM_IRQ, (byte) 0x80);
        setBitMask(REGISTER_FIFO_LEVEL, (byte) 0x80);
        writeRegister(REGISTER_COMMAND, COMMAND_IDLE);

        for(byte d : data){
            writeRegister(REGISTER_FIFO_DATA, d);
        }

        writeRegister(REGISTER_COMMAND, command);
        if(command == COMMAND_TRANSCEIVE){
            setBitMask(REGISTER_BIT_FRAMING, (byte) 0x80);
        }
        int i = 2000;
        byte n = 0;
        while(true){
            n = readRegister(REGISTER_COM_IRQ);
            i--;
            if(!((i != 0) && !((n & 0x01) > 0) && !((n & irqWait) > 0)))
                break;
        }
        clearBitMask(REGISTER_BIT_FRAMING, (byte) 0x80);

        if(i != 0){
            if((readRegister(REGISTER_ERROR) & 0x1B) == 0x00){
                if ((n & irq & 0x01) > 0) {
                    success = false;
                }

                if(command == COMMAND_TRANSCEIVE){
                    n = readRegister(REGISTER_FIFO_LEVEL);
                    lastBits = (byte) (readRegister(REGISTER_CONTROL) & 0x07);
                    if(lastBits != 0){
                        backLength = (n-1)* 8 + lastBits;
                    }else{
                        backLength = n*8;
                    }

                    if(n == 0){
                        n = 1;
                    }

                    if( n > MAX_LENGTH){
                        n = MAX_LENGTH;
                    }

                    for(i = 0; i < n; i++){
                        backData[i] = readRegister(REGISTER_FIFO_DATA);
                    }
                }
            }else {
                return false;
            }
        }
        return success;
    }

    public boolean request(){
        return this.request(COMMAND_REQUIRE_ID);
    }

    public boolean request(byte requestMode){
        byte tagType[]=new byte[]{requestMode};

        writeRegister(REGISTER_BIT_FRAMING, (byte) 0x07);

        boolean success =  writeCard(COMMAND_TRANSCEIVE, tagType);
        if(!success || backLength != 0x10){
            backLength = 0;
            success = false;
        }
        return success;

    }

    /**
     * Checks for collision errors
     * @return true if there are no collision errors
     */
    public boolean antiCollisionDetect(){
        int serial_number_check = 0;
        int i;

        writeRegister(REGISTER_BIT_FRAMING, (byte) 0x00);
        byte[] serial_number = new byte[]{COMMAND_ANTICOLLISION, 0x20};

        boolean success = writeCard(COMMAND_TRANSCEIVE,serial_number);
        if(success){
            if(backData.length == 5){
                for(i=0; i < 4; i++){
                    serial_number_check ^= backData[i];
                }
                if(serial_number_check != backData[4]){
                    return false;
                }
            }
            uid = backData;
        }
        return success;
    }

    private byte[] calculateCrc(byte[] data){
        clearBitMask(REGISTER_DIV_IRQ, (byte) 0x04);
        setBitMask(REGISTER_FIFO_LEVEL, (byte) 0x80);

        for(int i = 0;i < data.length-2; i++){
            writeRegister(REGISTER_FIFO_DATA, data[i]);
        }
        writeRegister(REGISTER_COMMAND, COMMAND_CALCULATE_CRC);
        int i = 255;
        byte n;
        while(true){
            n = readRegister(REGISTER_DIV_IRQ);
            i--;
            if((i == 0) || ((n & 0x04)> 0)){
                break;
            }
        }
        return new byte[]{readRegister(REGISTER_CRC_RESULT_LOW),readRegister(REGISTER_CRC_RESULT_HIGH)};
    }

    /**
     * Selects the tag to be used in following operations
     * @param uid Byte array containing the tag's uid
     * @return true if no errors occured
     */
    public boolean selectTag(byte[] uid){
        boolean success;
        byte data[]=  new byte[9];
        int i,j;

        data[0]=COMMAND_SELECT;
        data[1]=0x70;

        for(i=0, j=2 ; i<5 ;i++, j++)
            data[j]=uid[i];

        byte[] crc = calculateCrc(data);
        data[7] = crc[0];
        data[8] = crc[1];

        success = writeCard(COMMAND_TRANSCEIVE, data);
        return success && backLength == 0x18;
    }

    /**
     * Authenticates the use of a specific address. The tag must be selected before.
     * @param authMode The authentication mode, {@link #AUTH_A} or {@link #AUTH_B}
     * @param blockAddress The byte address of the block to authenticate for
     * @param key A six byte array containing the key used to authenticate
     * @return true if authentication was successful
     */
    public boolean authenticateCard(byte authMode,byte blockAddress,byte[] key) {
        byte data[] = new byte[12];
        int i, j;

        data[0] = authMode;
        data[1] = blockAddress;
        for (i = 0, j = 2; i < 6; i++, j++)
            data[j] = key[i];
        for (i = 0, j = 8; i < 4; i++, j++)
            data[j] = uid[i];

        boolean success = writeCard(COMMAND_MF_AUTHENT, data);
        if((readRegister(REGISTER_RXTX_STATUS) & 0x08) == 0){
            return false;
        }
        if(success){
            this.authenticated = true;
        }
        return success;
    }

    /**
     * @deprecated use {@link #authenticateCard(byte, byte, byte[])}
     */
    @Deprecated
    public boolean authenticateCard(byte authMode,byte blockAddress,byte[] key, byte[] uid) {
        this.uid = uid;
        return authenticateCard(authMode, blockAddress, key);
    }


    /**
     * Ends operations that use crypto and cleans up
     */
    public void stopCrypto(){
        clearBitMask(REGISTER_RXTX_STATUS, (byte) 0x08);
        this.authenticated = false;
    }

    /**
     * Reads the current data stored in the tag's block.
     * Authentication is required
     * @param blockAddress the block to read data from
     * @param buffer the byte array to store the read data to. Length must be 16
     * @return true if reading was successful
     */
    public boolean readBlock(byte blockAddress, byte[] buffer){
        byte data[]=new byte[4];
        data[0]=COMMAND_READ;
        data[1]=blockAddress;
        byte[] crc = calculateCrc(data);
        data[2] = crc[0];
        data[3] = crc[1];
        boolean success = writeCard(COMMAND_TRANSCEIVE, data);
        if(!success){
            return false;
        }
        if(backData.length != 16){
            return false;
        }
        System.arraycopy(backData, 0, buffer, 0, backData.length);
        return true;
    }

    /**
     * @deprecated Use {@link #readBlock(byte, byte[])} as it can report read status
     * @param blockAddress the byte address of the block to read from
     * @return 16 bytes array of the current value in that block
     */
    @Deprecated
    public byte[] readBlock(byte blockAddress){
        byte value[] = new byte[16];
        readBlock(blockAddress,value);
        return value;
    }

    /**
     * Writes data to a block in the tag
     * Authentication is required
     * @param blockAddress the byte address of the block to write to
     * @param data 16 byte array with the data that wants to be written
     * @return true if writing was successful
     */
    public boolean writeBlock(byte blockAddress, byte[] data) {
        byte buff[] = new byte[4];
        buff[0] = COMMAND_WRITE;
        buff[1] = blockAddress;
        byte[] crc = calculateCrc(buff);
        buff[2] = crc[0];
        buff[3] = crc[1];

        boolean success = writeCard(COMMAND_TRANSCEIVE, buff);
        if (!success) {
            return false;
        }
        if (backLength != 4 || (backData[0] & 0x0F) != 0x0A) {
            return false;
        }

        byte buffWrite[] = new byte[data.length + 2];
        System.arraycopy(data, 0, buffWrite, 0, data.length);
        crc = calculateCrc(buffWrite);
        buffWrite[buffWrite.length - 2] = crc[0];
        buffWrite[buffWrite.length - 1] = crc[1];
        success = writeCard(COMMAND_TRANSCEIVE, buffWrite);
        if(!success) {
            return false;
        }
        return !(backLength != 4 || (backData[0] & 0x0F) != 0x0A);
    }

    /**
     * @see #writeBlock(byte, byte[])
     * @deprecated use {@link #writeBlock(byte, byte[])}
     */
    @Deprecated
    public boolean write(byte blockAddress, byte[] data){
        return writeBlock(blockAddress, data);
    }

    /**
     * MIFARE tags blocks are organized in sectors, this calculates the address of a block in a
     * specific sector
     * @param sector the sector number
     * @param block the sector's block
     * @return the block's absolute address
     */
    public static byte getBlockAddress(byte sector, byte block){
        return (byte) (sector * 4 + block);
    }

    /**
     * @see #getBlockAddress(byte, byte)
     */
    public static byte getBlockAddress(int sector, int block){
        return getBlockAddress((byte)sector, (byte)block);
    }

    /**
     * Returns a string representation of the last read tag's UID
     * @param separator The character that separates each element of the uid
     * @return A string representing the tag's UID
     */
    public String getUidString(String separator){
        if(this.uid == null){
            return "";
        }
        StringBuilder sb = new StringBuilder();
        String prefix = "";
        for(byte b : this.uid){
            int ubyte = b&0xff;
            if(ubyte == 0){
                break;
            }
            sb.append(prefix);
            prefix = separator;
            sb.append(ubyte);
        }
        return sb.toString();
    }

    /**
     * @see #getUidString(), uses "-" as a separator by default
     */
    public String getUidString(){
        return getUidString("-");
    }

    private final static char[] HEX_CHARS = "0123456789ABCDEF".toCharArray();

    /**
     * Converts a data byte array to a string representing its 16 bytes in hexadecimal
     * @param data the byte array holding the data
     * @return A string representing the block's data
     */
    static public String dataToHexString(byte[] data){
        char[] buffer = new char[32];
        for(int i = 0; i < data.length; i++){
            int b = data[i] & 0xFF;
            buffer[i*2] = HEX_CHARS[b >>> 4];
            buffer[i*2+1] = HEX_CHARS[b & 0x0F];
        }
        return new String(buffer);
    }

}
