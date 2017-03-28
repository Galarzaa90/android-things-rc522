package com.galarzaa.androidthings;

import android.content.Context;
import android.os.Handler;

import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.GpioCallback;
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
    private final Context context;
    private final Handler handler;
    private RfidListener listener;

    private SpiDevice device;
    private Gpio resetPin;
    private Gpio irqPin;
    private int busSpeed = 1000000;

    public byte[] backData;
    public int backLength;

    private boolean irq = false;

    private IrqRunnable irqRunnable = new IrqRunnable();
    private Thread irqThread;

    private static final int MAX_LENGTH = 16;

    /* Found in Table 149, page 70*/
    private static final byte COMMAND_IDLE = 0x00;
    private static final byte COMMAND_CALCULATE_CRC = 0x03;
    private static final byte COMMAND_TRANSMIT = 0x04;
    private static final byte COMMAND_RECEIVE = 0x08;
    private static final byte COMMAND_TRANSCEIVE = 0x0C;
    private static final byte COMMAND_MF_AUTHENT = 0x0E;
    private static final byte COMMAND_SOFT_RESET = 0x0F;

    private static final byte AUTH_A = 0x60;
    private static final byte AUTH_B = 0x61;

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
    private static final byte REGISTER_COMMAND = 0x01;
    private static final byte REGISTER_INTERRUPT_ENABLE = 0x02;
    private static final byte REGISTER_COM_IRQ = 0x04;
    private static final byte REGISTER_DIV_IRQ = 0x05;
    private static final byte REGISTER_ERROR = 0x06;
    private static final byte REGISTER_COMMUNICATION_STATUS = 0x07;
    private static final byte REGISTER_RXTX_STATUS = 0x08;
    private static final byte REGISTER_FIFO_DATA = 0x09;
    private static final byte REGISTER_FIFO_LEVEL = 0x0A;
    private static final byte REGISTER_CONTROL = 0x0C;
    private static final byte REGISTER_CRC_RESULT_HIGH = 0x21;
    private static final byte REGISTER_CRC_RESULT_LOW = 0x22;
    private static final byte REGISTER_TIMER_MODE = 0x2A;
    private static final byte REGISTER_TIMER_PRESCALER_MODE = 0x2B;
    private static final byte REGISTER_TIMER_RELOAD_HIGH = 0x2C;
    private static final byte REGISTER_TIMER_RELOAD_LOW = 0x2D;
    private static final byte REGISTER_TX_CONTROL = 0x14;
    private static final byte REGISTER_TX_MODE = 0x15;
    private static final byte REGISTER_MODE = 0x11;
    private static final byte REGISTER_BIT_FRAMING = 0x0D;



    public Rc522(Context context, SpiDevice spiDevice, Gpio resetPin){
        this.context = context;
        this.device = spiDevice;
        this.resetPin = resetPin;
        this.handler = new Handler(context.getMainLooper());
        initializeDevice();
    }

    public Rc522(Context context, SpiDevice spiDevice, Gpio resetPin, int speed){
        this.context = context;
        this.device = spiDevice;
        this.resetPin = resetPin;
        this.handler = new Handler(context.getMainLooper());
        this.busSpeed = speed;
        initializeDevice();
    }

    private void setListener(RfidListener listener){
        this.listener = listener;
    }

    private void initializeDevice(){
        try {
            device.setFrequency(busSpeed);
            resetPin.setDirection(Gpio.DIRECTION_OUT_INITIALLY_HIGH);
            /*irqPin.setDirection(Gpio.DIRECTION_IN);
            irqPin.setEdgeTriggerType(Gpio.EDGE_FALLING);
            irqPin.registerGpioCallback(new IrqCallback());*/
        } catch (IOException e) {
            e.printStackTrace();
        }
        reset();
        spiWrite(REGISTER_TIMER_MODE, (byte) 0x8D);
        spiWrite(REGISTER_TIMER_PRESCALER_MODE, (byte) 0x3E);
        spiWrite(REGISTER_TIMER_RELOAD_LOW, (byte) 30);
        spiWrite(REGISTER_TIMER_RELOAD_HIGH, (byte) 0);
        spiWrite(REGISTER_TX_MODE, (byte) 0x40);
        spiWrite(REGISTER_MODE, (byte) 0x3D);
        setAntenna(true);

    }

    private void reset(){
        spiWrite(REGISTER_COMMAND, COMMAND_SOFT_RESET);
    }

    private void spiWrite(byte address, byte value){
        byte buffer[] = {(byte) (((address << 1) & 0x7E)), value};
        byte response[] = new byte[buffer.length];
        try {
            device.transfer(buffer, response, buffer.length);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private byte spiRead(byte address){
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

    private void setAntenna(boolean enabled){
        if(enabled){
            byte currentState = spiRead(REGISTER_TX_CONTROL);
            if((currentState & 0x03) != 0x03){
                setBitMask(REGISTER_TX_CONTROL, (byte) 0x03);
            }
        }else{
            clearBitMask(REGISTER_TX_CONTROL, (byte) 0x03);
        }
    }

    private void setBitMask(byte address, byte mask){
        byte value = spiRead(address);
        spiWrite(address, (byte) (value | mask));
    }

    private void clearBitMask(byte address, byte mask){
        byte value = spiRead(address);
        spiWrite(address, (byte) (value & (~mask)));
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
        spiWrite(REGISTER_INTERRUPT_ENABLE, (byte) (irq | 0x80));
        clearBitMask(REGISTER_COM_IRQ, (byte) 0x80);
        setBitMask(REGISTER_FIFO_LEVEL, (byte) 0x80);
        spiWrite(REGISTER_COMMAND, COMMAND_IDLE);

        for(byte d : data){
            spiWrite(REGISTER_FIFO_DATA, d);
        }

        spiWrite(REGISTER_COMMAND, command);
        if(command == COMMAND_TRANSCEIVE){
            setBitMask(REGISTER_BIT_FRAMING, (byte) 0x80);
        }
        int i = 2000;
        byte n = 0;
        while(true){
            n = spiRead(REGISTER_COM_IRQ);
            i--;
            if ((i == 0) || (n & 0x01) > 0 || (n & irqWait) > 0){
                break;
            }
        }
        clearBitMask(REGISTER_BIT_FRAMING, (byte) 0x80);

        if(i != 0){
            if((spiRead(REGISTER_ERROR) & 0x1B) == 0x00){
                success = true;

                if ((n & irq & 0x01) > 0) {
                    success = false;
                }

                if(command == COMMAND_TRANSCEIVE){
                    n = spiRead(REGISTER_FIFO_LEVEL);
                    lastBits = (byte) (spiRead(REGISTER_CONTROL) & 0x07);
                    if(lastBits != 0){
                        backLength = (n-1)* 8 + lastBits;
                    }else{
                        backLength = n*8;
                    }

                    if(n == 0){
                        n = 1;
                    }

                    if( n > MAX_LENGTH){
                        n = (byte) MAX_LENGTH;
                    }

                    for(i = 0; i < n; i++){
                        backData[i] = spiRead(REGISTER_FIFO_DATA);
                    }
                }
            }else {
                success = false;
            }
        }
        return success;
    }

    public boolean request(){
        return this.request(COMMAND_REQUIRE_ID);
    }

    public boolean request(byte requestMode){
        byte tagType[]=new byte[]{requestMode};

        spiWrite(REGISTER_BIT_FRAMING, (byte) 0x07);

        boolean success =  writeCard(COMMAND_TRANSCEIVE, tagType);
        if(!success || backLength != 0x10){
            backLength = 0;
            success = false;
        }
        return success;

    }

    public boolean antiCollisionDetect(){
        byte[] serial_number = new byte[2];
        int serial_number_check = 0;
        int i;

        spiWrite(REGISTER_BIT_FRAMING, (byte) 0x00);
        serial_number[0] = COMMAND_ANTICOLLISION;
        serial_number[1] = 0x20;

        boolean success = writeCard(COMMAND_TRANSCEIVE,serial_number);
        if(success){
            if(backData.length == 5){
                for(i=0; i < 4; i++){
                    serial_number_check ^= backData[i];
                }
                if(serial_number_check != backData[4]){
                    success = false;
                }
            }else{
                success = true;
            }
        }
        return success;
    }

    private byte[] calculateCrc(byte[] data){
        byte[] returnData = new byte[2];
        clearBitMask(REGISTER_DIV_IRQ, (byte) 0x04);
        setBitMask(REGISTER_FIFO_LEVEL, (byte) 0x80);
        for(int i = 0;i < data.length-2; i++){
            spiWrite(REGISTER_FIFO_DATA, data[i]);
        }
        spiWrite(REGISTER_COMMAND, COMMAND_CALCULATE_CRC);
        int i = 255;
        byte n;
        while(true){
            n = spiRead(REGISTER_DIV_IRQ);
            i--;
            if((i == 0) || ((n & 0x04)> 0)){
                break;
            }
        }
        returnData[0] = spiRead(REGISTER_CRC_RESULT_LOW);
        returnData[1] = spiRead(REGISTER_CRC_RESULT_HIGH);
        return returnData;
    }

    public boolean selectTag(byte [] uid){
        boolean success;
        byte data[]=new byte[9];
        int i,j;

        data[0]=COMMAND_SELECT;
        data[1]=0x70;

        for(i=0,j=2;i<5;i++,j++)
            data[j]=uid[i];

        byte[] crc = calculateCrc(data);
        data[7] = crc[0];
        data[8] = crc[1];

        success=writeCard(COMMAND_TRANSCEIVE, data);
        if (success && backLength == 0x18){
            return true;
        }
        else{
            return false;
        }
    }

    public boolean authenticateCard(byte auth_mode,byte block_address,byte []key,byte []uid){
        byte data[]=new byte[12];
        int i,j;

        data[0]=auth_mode;
        data[1]=block_address;
        for(i=0,j=2;i<6;i++,j++)
            data[j]=key[i];
        for(i=0,j=8;i<4;i++,j++)
            data[j]=uid[i];

        boolean success = writeCard(COMMAND_MF_AUTHENT, data);
        if((spiRead(REGISTER_RXTX_STATUS) & 0x08) == 0){
            success = false;
        }
        return success;
    }

    public void stopCrypto(){
        clearBitMask(REGISTER_RXTX_STATUS, (byte) 0x08);
    }

    public boolean read(byte block_address)
    {
        byte data[]=new byte[4];
        data[0]=COMMAND_READ;
        data[1]=block_address;
        byte[] crc = calculateCrc(data);
        data[2] = crc[0];
        data[3] = crc[1];
        boolean success = writeCard(COMMAND_TRANSCEIVE, data);
        if(backLength != 16){
            success = false;
        }
        return success;
    }

    public boolean write(byte blockAddress, byte[]data)
    {
        byte buff[]=new byte[4];
        buff[0]=COMMAND_WRITE;
        buff[1]=blockAddress;
        byte[] crc = calculateCrc(buff);
        buff[2] = crc[0];
        buff[3] = crc[1];

        boolean success = writeCard(COMMAND_TRANSCEIVE, buff);
        if(!success || backLength != 4 || (backData[0] & 0x0F) != 0x0A){
            success = false;
        }

        if(success){
            byte buffWrite[]=new byte[data.length+2];
            for (int i=0;i<data.length;i++) {
                buffWrite[i] = data[i];
            }
            crc = calculateCrc(buffWrite);
            buffWrite[data.length-2] = crc[0];
            buffWrite[data.length-1] = crc[1];
            success = writeCard(COMMAND_TRANSCEIVE, buffWrite);

            if(backLength !=4 || (backData[0] & 0x0F) != 0x0A){
                success = false;
            }
        }
        return success;
    }

    private void startCardReader(){
        this.initializeDevice();
        irqRunnable.terminate();
        spiWrite(REGISTER_COM_IRQ, (byte) 0x00);
        spiWrite(REGISTER_INTERRUPT_ENABLE, (byte) 0xA0);
        irqThread = new Thread();
        irqThread.run();
    }


    private class IrqCallback extends GpioCallback {

        @Override
        public boolean onGpioEdge(Gpio gpio){
            irq = true;
            return true;
        }
    }


    private class IrqRunnable implements Runnable{
        private volatile boolean running = true;

        void terminate(){
            running = false;
        }
        @Override
        public void run() {
            while(running && !irq){
                spiWrite(REGISTER_FIFO_DATA, (byte) 0x26);
                spiWrite(REGISTER_COMMAND, COMMAND_TRANSCEIVE);
                spiWrite(REGISTER_BIT_FRAMING, (byte) 0x87);
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if(irq){
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            if(listener != null){
                                listener.onRfidDetected();
                            }
                        }
                    });
                }
            }
        }
    }

    public interface RfidListener {
        void onRfidDetected();
    }
}
