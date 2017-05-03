# Android Things RC522 [![Bintray](https://img.shields.io/bintray/v/galarzaa90/maven/android-things-rc522.svg)](https://bintray.com/galarzaa90/maven/android-things-rc522) [![license](https://img.shields.io/github/license/Galarzaa90/android-things-rc522.svg)]() [![Android Things](https://img.shields.io/badge/android--things-0.2--devpreview-red.svg)](https://developer.android.com/things/preview/releases.html#developer_preview_2)

An Android Things libray to control RFID readers based on the RC522 readers.

Based on [pi-rc522](https://github.com/ondryaso/pi-rc522) by user **ondryaso**

## Connections
The connections vary based on the [board](https://developer.android.com/things/hardware/developer-kits.html) used.

**RST** and **IRQ** pins can be configured programatically.

## Installing
This library is available at Bintray. To install add this to your module's build.gradle
```groovy
repositories {
    maven {
        url 'http://dl.bintray.com/galarzaa90/maven/'
    }
}


dependencies {
    compile 'com.galarzaa.android-things:rc522:0.1.1'
}
```

## Usage
_The use of interruptions hasn't been tested._

There's two ways to use this, one is to constantly poll the RC522 until a card is found, and then 
performs any operations you want.

Unfortunately, in Android, the UI thread shouldn't be blocked, so the polling has to be done on a 
separate thread e.g. AsyncTask, Runnable, etc.

To use the libary, a `SpiDevice` object must be passed in the constructor, along with a `Gpio` object for
the RST pin and a `Gpio` object for the IRQ pin.

### Polling state
```java
public class MainActivty extends AppCompatActivity{
    private Rc522 mRc522;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PeripheralManagerService pioService = new PeripheralManagerService();
        try {
            /* Names based on Raspberry Pi 3 */
            SpiDevice spiDevice = pioService.openSpiDevice("SPI0.0");
            Gpio resetPin = pioService.openGpio("BCM25");
            mRc522 = new Rc522(this, spiDevice, resetPin);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private void readRFid(){
        while(true){
            boolean success = rc522.request();
            if(success){
                success = rc522.antiCollisionDetect();
                if(success){
                    byte[] uuid = rc522.getUiid();
                    /* Perform any operations here */
                    break;
                }
            }
        }
    }
}
```

### Using Listener
_Feature not tested_  
As of [Developer Preview 2](https://developer.android.com/things/preview/releases.html#developer_preview_2)
it is not possible to active the internal pull up resistors programatically. The pin used for IRQ 
must be pulled up to work. An external pull up is required

Note that in Raspberry Pi, GPIO pins **BCM4**, **BCM5** and **BCM6** are internally pulled up by default.
```java
public class MainActivty extends AppCompatActivity implements Rc522.RfidListener {
    private Rc522 rc522;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PeripheralManagerService pioService = new PeripheralManagerService();
        try {
            /* Names based on Raspberry Pi 3 */
            SpiDevice spiDevice = pioService.openSpiDevice("SPI0.0");
            Gpio resetPin = pioService.openGpio("BCM25");
            Gpio irqPin = pioService.openGpio("BCM4");
            mRc522 = new Rc522(this, spiDevice, resetPin, irqPin);
            mRc522.setListener(this);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    @Override
    public void onRfidDetected() {
        boolean success = rc522.request();
        if(success){
            success = rc522.antiCollisionDetect();
            if(success){
                byte[] uuid = rc522.getUiid();
                /* Perform any operations here */
            }
        }
    }
}
```
## Contributing
This library is still in development, suggestions, improvements and fixes are welcome. Please 
submit a **pull request**
