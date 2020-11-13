/**
****************************************************************************

@author Stan Green
Copyright (C) 2009 Stan Green

This source code is free software; you can redistribute it and/or modify it under
the terms of the GNU General Public License as published by the
Free Software Foundation; either version 3.0 of the License, or (at your
option) any later version.

This source code is distributed in the hope that it will be useful, but WITHOUT
ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
details.

 You should have received a copy of the GNU General Public License along
 with this source code; if not, write to the Free Software Foundation, Inc.,
 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
**************************************************************************** 
*/

/*
Change Log:
1/4/2011 : Stan Green - Added the ability to capture while convert is off and the ability to turn off capture.
1/5/2011 : Stan Green - Added the ability to add and manage permanent clip items.
1/7/2011 : Stan Green - Added the ability to encrypt the data in memory (Except for the menu as they would be unreadable!)
           and on the hard drive.
1/19/2011: Stan Green - Added password for encryption.
3/31/2011: Stan Green - Was seeing an odd issue so I move just one line of code. Will watch and see if it happens again.
5/10/2011: Fixed issue with perm items being in the wrong order.
??/??/????: Change the Perm Items to add to the bottom rather than push down. 
5/30/2013: Integrated with C++, using Java callbacks, to watch the clipboard. (i.e. Stop using a polling process.)
5/30/2013: Added serialVersionUID to PDStack so as not to loose saved file when recompiling the appliciaton.
5/30/2013: Converted all println to an interal method to standardize the debuging output.
6/28/2020: Converted to 64bit Java using OpenJdk. Concerted WatchClipboard to 64bit.
*/

/*
To Do:
On start up get the current clipboard.
Add Option to Trim current item. (remove bullets, CR/LF Spaces, Tabs, etc.)
When selecting an item from the menu, move it to positon 1.
When the same text is copied a second time, don't replace the contents on the cipboard.
Add the images to the type of capture.
*/

import java.awt.*;
import java.awt.event.*;
import java.awt.datatransfer.*;
import javax.swing.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.text.*;

import javax.crypto.*;
import javax.crypto.spec.*;
import java.security.spec.*;

//Image support
import javax.imageio.*;
import java.awt.image.*;

import org.apache.commons.codec.digest.*;

public class JTClipper implements MouseListener, ActionListener, ItemListener
{
	//Note: (Replaced with C++ on 5/30. This is no longer an issue.)
	//Java cannot monitor the clipboard for changes. This is was a design decision made by Sun.
	//This forces us to use endless loop to check for available data. It is unknown if the
	//data is new or the same data a the previous run. Therefore this code uses a "brute force"
	//approach to solving this problem. In theory this could slow down the system, but I have 
	//never seen a noticiable slowdown.  This does not seem to have a negative 
	//impact on the system performance as most activity is in memory. 
	//If vary large text is copied, this may cause a slight slowdown. This could also 
	//cause an issue in the case of very low disk space as the memory is saved to the local 
	//disk when data changes.
	//If pasting to fast (i.e. less than half a second.) the conversion may not have taken place yet.

private int iiPDStackSize = 30;
private int iiPDImageStackSize = 30;
private int iiClipCurrent = 0;
private int iiMenuCount = 0;
private int iiMaxDebugLength = 80;
private int iiPermCount = 0;
private int iiPermMax = 20;
private int iiImageCount = 0;
private int iiImageMax = 20;
private int iiMaxPermMenuItemSize = 30;
private int iiMaxMenuItemSize = 23;
//private int iiCheckTime = 500; //Pause for 1/2 a second.

private boolean ibExit = false;
private boolean ibDebug = false;
private boolean ibCapture = true;
private boolean ibConvert = true;
//private boolean ibRunConvert = true;
private boolean ibEncrypted = false;
private boolean ibPWChecked = false;
private boolean DLLFound=true;
private boolean ibImages = false;
private boolean ibIsImage = false;
private Boolean ioEncrypted = null;

private String isSaveFile = null;
private String isPWCheck = ""; //Validate the password.

private Image imageOff = null;
private Image imageOn = null;

private TrayIcon ioTrayIcon = null;

private PDStack ioCBVersions = new PDStack(iiPDStackSize);
private PDStack ioCBVersionsMD5 = new PDStack(iiPDStackSize);
private PDStack ioClipItem = new PDStack(iiPDStackSize);

//Base Meun
private MenuItem ioExit = null;
private MenuItem ioClearAll = null;
private MenuItem ioClearItem = null;
private MenuItem ioSetMaxItems = null;
private MenuItem ioImages = null;
private CheckboxMenuItem ioCBEncrypted = null;
private CheckboxMenuItem ioCapture = null;
private CheckboxMenuItem ioCBImages = null;
private PopupMenu ioPopupMenu = null;

//The permanent clip items.
private PopupMenu ioPermItemsPopupMenu = null;
private PDStack ioPermClipItem = new PDStack(iiPermMax);
private PDStack ioPermCBVersions = new PDStack(iiPermMax);
private MenuItem ioMakePerm = null;
private MenuItem ioRemovePerm = null;

//Images
private Image ioImage = null;
private PDStack ioCBImageVersion = new PDStack(iiPDImageStackSize);
private PDStack ioCBImageVersionMD5 = new PDStack(iiPDImageStackSize);
private PDStack ioCBImage = new PDStack(iiPDImageStackSize);

//Encryption Support
private Cipher ioEncrypt = null;
private Cipher ioDecrypt = null;
private static byte[] ibtSalt = { (byte) 0xA9, (byte) 0x9B, (byte) 0xC8, (byte) 0x32, (byte) 0x56, (byte) 0x35, (byte) 0xE3, (byte) 0x03 };

//The Clipboard.
private Clipboard ioClipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
private Transferable ioTrans = null;

//Unnneded functions, but Java makes us use them any way.
public void mouseExited(MouseEvent e){}
public void mouseEntered(MouseEvent e){}
public void mouseReleased(MouseEvent e){}
public void mousePressed(MouseEvent e) {}

native void DLLInit();

public void clipboardChange()
{
	//The clibboard changed so run the conversion process.
	if(ibDebug)
	{
		printLine("ClipboardChange Triggered");
	}
	
//	This code is needed due to a design flaw in Adobe Reader. If we run to fast, Reader gets messed up. So, we slow 
//	down just a bit to account for this.
	try
	{
		Thread.sleep(100);
	}
	catch(Exception e)
	{
		printLine("Exception - " + e);
		printLine("StackTrace: ");
		e.printStackTrace(System.out);
	}
	runCC();
}

public void printLine(String asMsg)
{
	System.out.println(getNow() + " : " + asMsg);
}

public static void main ( String[] asArgs )
{
	JTClipper loJTClipper = new JTClipper(asArgs);
}

public JTClipper(String[] asArgs)
{
	boolean lbSuccess = false;
	
	// This is a workaround for a bug in the JVM. 
	// http://stackoverflow.com/questions/13575224/comparison-method-violates-its-general-contract-timsort-and-gridlayout
	System.setProperty("java.util.Arrays.useLegacyMergeSort", "true");
	
	//Need to clear the clipboard so we don't get unexpected data.
	StringSelection selection = new StringSelection("");
	
	//There was a race conditon with setContents with the Windows clipboard not being ready.
	//Adding this try block actual slows down the code enough to resolved the race conditon. 
	//This code is left here in case the race conditon returns.
	while (!lbSuccess)
	{
		try
		{
			ioClipboard.setContents(selection, selection);
			lbSuccess = true;
		}
		catch (IllegalStateException e)
		{
			Thread.sleep(100);
			lbSuccess = false;
		}
	}
	
	
	
	//Get the command line args. Supported are save file name (-s) and debug mode (-d).
	CmdLineParser loParser = new CmdLineParser();
	
	//Turn on debug
	CmdLineParser.Option loDebug = loParser.addBooleanOption('d', "debug");
	
	//Check for a debug length. Only used if debug is on.
	CmdLineParser.Option loDebugLength = loParser.addIntegerOption('l', "length");
	
	//Get the file name
	CmdLineParser.Option loFile = loParser.addStringOption('s', "savefile");
	
	try
	{
		loParser.parse(asArgs);
	}
	catch ( CmdLineParser.OptionException e )
	{
		printLine(e.getMessage());
		ibExit = true;
		System.exit(-1);
	}
	
	Boolean lobDebug = (Boolean)loParser.getOptionValue(loDebug);
	Integer loiDebugLength = (Integer)loParser.getOptionValue(loDebugLength);
	String lsSaveFile = (String)loParser.getOptionValue(loFile);
	
	if(lobDebug != null)
	{
		ibDebug = lobDebug.booleanValue();
		printLine("***** STARTING JTClipper ****");
		printLine("Debug is on.");
	}
	
	if(loiDebugLength != null)
	{
		iiMaxDebugLength = loiDebugLength.intValue();
		if(ibDebug)
		{
			printLine("Debug Output Length: " + iiMaxDebugLength);
		}
	}
	
	isSaveFile = setFileName(lsSaveFile);
	
	if(ibDebug)
	{
		printLine("Save file name: " + isSaveFile);
	}

	try
	{
		System.loadLibrary("WatchClipboard");
		if(ibDebug)
		{
			printLine("Loaded DLL");
		}
	}
	catch (UnsatisfiedLinkError e)
	{
		DLLFound = false;
		if(ibDebug)
		{
			printLine("DLL Not Found");
		}
	}
	
	//Init the DLL
	DLLInit();
	
	//Load the saved state.
	loadState();

	//Build the system tray icon.
	trayIcon();
	
//	//Start the conversion process.
//	convertText();
}

private void initEncryption(String asPassPhrase)
{
	try
	{
	int liIterationCount = 2;
    KeySpec loKeySpec = new PBEKeySpec(asPassPhrase.toCharArray(), ibtSalt, liIterationCount);
    SecretKey loKey = SecretKeyFactory.getInstance("PBEWithMD5AndDES").generateSecret(loKeySpec);
    ioEncrypt = Cipher.getInstance(loKey.getAlgorithm());
    ioDecrypt = Cipher.getInstance(loKey.getAlgorithm());

    AlgorithmParameterSpec loParamSpec = new PBEParameterSpec(ibtSalt, liIterationCount);

    ioEncrypt.init(Cipher.ENCRYPT_MODE, loKey, loParamSpec);
    ioDecrypt.init(Cipher.DECRYPT_MODE, loKey, loParamSpec);
	}
	catch (javax.crypto.NoSuchPaddingException e)
	{
		printLine("Exception: " + e);
	}
	catch (java.security.NoSuchAlgorithmException e)
	{
		printLine("Exception: " + e);
	}
	catch (java.security.InvalidKeyException e)
	{
		printLine("Exception: " + e);
	}
	catch (java.security.spec.InvalidKeySpecException e)
	{
		printLine("Exception: " + e);
	}
	catch (java.security.InvalidAlgorithmParameterException e)
	{
		printLine("Exception: " + e);
	}
}

private String getNow()
{
	SimpleDateFormat loDTFormat = new SimpleDateFormat("MM/dd/yyyy hh:mm:ss.S");
	String lsDateTime = loDTFormat.format(new Date());
	return lsDateTime;
}

private void runCC()
{
	if(ibDebug)
	{
		printLine("In runCC");
	}
	String lsClipData = "";
	boolean lbNoStringMsg = false; //Keep from printing, when in debug mode, the same thing every second.
	
//	while (ibRunConvert)
//	if (ibRunConvert)
//	{
		try
		{
			if(ibCapture)
			{
				ioTrans = ioClipboard.getContents(null);
				
				if(ibImages && ioTrans.isDataFlavorSupported(DataFlavor.imageFlavor))
				{
					ioImage = (Image)ioTrans.getTransferData(DataFlavor.imageFlavor);
					ibIsImage = true;
					if(ibDebug)
					{
						printLine("Image on Clipboard");
					}
				}
				else
				{
					lsClipData = (String)ioClipboard.getData(DataFlavor.stringFlavor);
					//This should NEVER happen, but I feel better having it here :-)
					if(lsClipData == null)
					{
						lsClipData = "";
					}
				}

				//If there is data push it into the stack and update the menu.
				if(lsClipData.length() > 0)
				{
					lbNoStringMsg = false;
					boolean ibChanged = false;
					
					//Build the text used for the menu item.
					String lsMenuData = this.buildMenuText(lsClipData);
							
					if(ioCBVersions.getSize() > 0)
					{
						if(!this.getClipData(iiClipCurrent).equals(lsClipData))
						{
							ibChanged = true;
							iiClipCurrent = 0; //This is the new current clip item.
						
							if(ibDebug)
							{
								printLine("Vector Count Before Push: " + ioCBVersions.getSize());
							}

							//Calculate the MD5 Sum.
							String lsMD5 = DigestUtils.md5Hex(lsClipData); 
							if(ibDebug)
							{
								printLine("MD5Sum: " + lsMD5);
							}
	
							//See if this is a duplicate item. If so, delete the old one.
							for (int i=0; i < iiMenuCount; i++)
							{
								String lsMD5Item = (String)ioCBVersionsMD5.get(i);
								
								if(ibDebug)
								{
									printLine("Menu Item MD5: " + lsMD5Item);
								}
								
								if(lsMD5.equals(lsMD5Item))
								{
									ioCBVersionsMD5.remove(i);
									ioCBVersions.remove(i);
									ioClipItem.remove(i);
									break;
								}
							}
							
							ioCBVersionsMD5.push(lsMD5);	
							
							if(ibDebug)
							{
								printLine("In: convertText");
								printLine("Clip Data: " + lsClipData);
							}
							this.pushClipData(lsClipData);
							
							if(ibDebug)
							{
								printLine("Vector Count After Push: " + ioCBVersions.getSize());
							}
							
							this.updateMenu(lsMenuData, true);
						
							if(ibDebug)
							{
								if(lsClipData.length() > iiMaxDebugLength)
								{
									printLine("Pushed data: " + lsClipData.substring(0,iiMaxDebugLength) + " (more: " +
										(lsClipData.length() - iiMaxDebugLength) + ")");
								}
								else
								{
									printLine("Pushed data: " + lsClipData);
								}
							}
						}
					}
					else
					{
						//First item on the stack.
						ibChanged = true;

						//Calculate the MD5 Sum to see if this is a duplicate strting.
						String lsMD5 = DigestUtils.md5Hex(lsClipData); 
						if(ibDebug)
						{
							printLine("MD5Sum: " + lsMD5);
						}

						this.pushClipData(lsClipData);
						ioCBVersionsMD5.push(lsMD5);
						
						this.updateMenu(lsMenuData, true);
						if(ibDebug)
						{
							if(lsClipData.length() > iiMaxDebugLength)
								{
									printLine("Pushed data: " + lsClipData.substring(0,iiMaxDebugLength) + " (more: " +
										(lsClipData.length() - iiMaxDebugLength) + ")");
								}
								else
								{
									printLine("Pushed data: " + lsClipData);
								}
						}
					}
				
					if(ibChanged)
					{
						if(ibDebug)
						{
							printLine("Data Changed");
							if(lsClipData.length() > iiMaxDebugLength)
							{
								printLine("Clipboard data: " + lsClipData.substring(0,iiMaxDebugLength) + " (more: " +
									(lsClipData.length() - iiMaxDebugLength) + ")");
							}
							else
							{
								printLine("Clipboard data: " + lsClipData);
							}
						}
						if(ibConvert)
						{	
							//Put the text back in the clipboard as plain text. (i.e. Convert from formated test to plan text.)
							StringSelection selection = new StringSelection(lsClipData);
							ioClipboard.setContents(selection, selection);
						}
						saveState();
					}
				}
//Image processing.
				else
				{
					if(ibDebug)
					{
						printLine("In Image Processing");
					}
				
					if( ibIsImage && ibImages && ioImage != null)
					{

						if(ibDebug)
						{
							int liImageHeight = ioImage.getHeight(null); 
							int liImageWidth = ioImage.getWidth(null);
							printLine("Image Height: " + liImageHeight);
							printLine("Image Width: " + liImageWidth);
						}
						
						BufferedImage loBuffImage = null;
						loBuffImage = (BufferedImage)ioImage;
						
						ByteArrayOutputStream loOutStream = new ByteArrayOutputStream();
						ImageIO.write( loBuffImage, "png" , loOutStream );
						loOutStream.flush();
						byte[] byteArray = loOutStream.toByteArray();
						loOutStream.close();
										
						//Calculate the MD5 Sum.
						String lsMD5 = DigestUtils.md5Hex(byteArray); 
						if(ibDebug)
						{
							printLine("MD5Sum: " + lsMD5);
						}
					}
				}
//end of image processing.
			}
		}
		catch(OutOfMemoryError e)
		{
			ibConvert = false;
			if(imageOff == null)
			{
				ClassLoader loClassLoader = this.getClass().getClassLoader();
				URL loImageURL = loClassLoader.getResource("images/ClipBoardConvertOff.gif");
				imageOff = Toolkit.getDefaultToolkit().createImage(loImageURL);
			}
			ioTrayIcon.setToolTip("Convert Off");
			ioTrayIcon.setImage(imageOff);
			
			JOptionPane.showMessageDialog
			(
				null, "The copied text is to big to fit in avaliable memory.\n" +
				"Clipboard conversion has been turned off to avoid an endless message loop.\n" +
				"You can use the Clear Item, Oldest or Clear All menu item to attempt to free memory.\n" +
				"If this issue continues, you may want to increase the maximum Java heap size (e.g. ?Xmx256m) " +
				"or reduce the Max Items.",
				"Out Of Memory Error", JOptionPane.ERROR_MESSAGE
			);
			
		}
		catch(UnsupportedFlavorException e)
		{
			if(ibDebug && !lbNoStringMsg)
			{
				lbNoStringMsg = true;
				printLine("No string on clipboard.");
			}
		}
	
		catch(IllegalStateException e)
		{
			if(ibDebug)
			{
				printLine("Exception - " + e);
				printLine("StackTrace: ");
				e.printStackTrace(System.out);
			}
		}
		
		catch(Exception e)
		{
			if(ibDebug)
			{
				printLine("Exception - " + e);
				printLine("StackTrace: ");
				e.printStackTrace(System.out);
			}
		}
/*
		try
		{
			Thread.sleep(iiCheckTime);
		}
		catch(Exception e)
		{
			printLine("Exception - " + e);
			printLine("StackTrace: ");
			e.printStackTrace(System.out);
		}
*/
//	}
}

private String buildMenuText(String asClipData)
{
	return buildMenuText(asClipData, iiMaxMenuItemSize);
}

private String buildMenuText(String asClipData, int aiSize )
{
	//Build the text used for the menu item.
	String lsMenuData = asClipData.trim();
	lsMenuData = lsMenuData.replace('\r', ' ');
	lsMenuData = lsMenuData.replace("\n", "");
	lsMenuData = lsMenuData.replace('\t', ' ');
	lsMenuData = lsMenuData.trim();
	lsMenuData = ") " + lsMenuData;
	if(lsMenuData.length() > aiSize)
	{
		lsMenuData = lsMenuData.substring(0,aiSize);
	}
	return lsMenuData;
}

private void trayIcon()
{
	SystemTray tray = null;
	
	if (SystemTray.isSupported())
	{
		tray = SystemTray.getSystemTray();
		
		ioPopupMenu = new PopupMenu();
		
		ioClearAll = new MenuItem("Clear All");
		ioClearAll.setName("ClearAll");
		ioClearAll.addActionListener(this);
		
		ioClearItem = new MenuItem("Clear Item");
		ioClearItem.setName("ClearItem");
		ioClearItem.addActionListener(this);
		
		ioExit = new MenuItem("Exit");
		ioExit.setName("Exit");
		ioExit.addActionListener(this);
		
		ioSetMaxItems = new MenuItem("Set Max Items");
		ioSetMaxItems.setName("SetMaxItems");
		ioSetMaxItems.addActionListener(this);
		
		ioCBImages  = new CheckboxMenuItem("Capture Images",false);
		ioCBImages.setName("CaptureImages");
		ioCBImages.addItemListener(this);
		
		ioImages = new MenuItem("Images");
		ioImages.setName("Images");
		ioImages.addActionListener(this);
		
		ioCapture = new CheckboxMenuItem("Capture",true);
		ioCapture.setName("Capture");
		if(ibDebug)
		{
			printLine("Capture State - Menu: " + ioCapture.paramString()  + "  Variable: " + ibCapture);
		}
		ioCapture.addItemListener(this);
		
		ioMakePerm = new MenuItem("Make Item Permanent");
		ioMakePerm.setName("MakeItemPermanent");
		ioMakePerm.addActionListener(this);
		
		ioRemovePerm = new MenuItem("Remove Permanent Item");
		ioRemovePerm.setName("RemovePermanentItem");
		ioRemovePerm.addActionListener(this);
		
		ioCBEncrypted = new CheckboxMenuItem("Encrypt", ibEncrypted);
		ioCBEncrypted.setName("Encrypted");
		ioCBEncrypted.addItemListener(this);
		
		ioPermItemsPopupMenu = new PopupMenu("Permanent Items");
		
		this.buildMenu();
		
		ClassLoader loClassLoader = this.getClass().getClassLoader();
		URL loImageURL = loClassLoader.getResource("images/ClipBoardConvertOn.gif");
		imageOn = Toolkit.getDefaultToolkit().createImage(loImageURL);
		loImageURL = loClassLoader.getResource("images/ClipBoardConvertOff.gif");
		imageOff = Toolkit.getDefaultToolkit().createImage(loImageURL);

		
		ioTrayIcon = new TrayIcon(imageOn, "Convert On", ioPopupMenu);
		ioTrayIcon.setImageAutoSize(true);
		ioTrayIcon.addActionListener(this);
		ioTrayIcon.addMouseListener(this);

		try
		{
			tray.add(ioTrayIcon);
		}
		catch (Exception e)
		{
			if(ibDebug)
			{
				printLine("TrayIcon could not be added.");
			}
		}	
			
	}
	else
	{
		printLine("System tray not supported.");
		ibExit = true;
		System.exit(-1);
	}
	
}


public String setFileName(String asFileName)
{
	String lsFileName = null;
	
	if(null != asFileName)
	{
		if(ibDebug)
		{
			printLine("Requested save file: " + asFileName);
		}
		
		//Add the file name if one is not provided.
		if(asFileName.endsWith(File.separator))
		{
			asFileName = asFileName + "JTClipperState.ser";
		}
		//Add the file name if one is not provided.
		if(asFileName.endsWith(":"))
		{
			asFileName = asFileName + File.separator + "JTClipperState.ser";
		}
				
		File loFileIsDir = new File(asFileName);
		if(loFileIsDir.isDirectory())
		{
			if(ibDebug)
			{
				printLine(asFileName + " is a directory.");
			}
			asFileName = asFileName + File.separator + "JTClipperState.ser";
		}
				
		File loSaveFile = new File(asFileName);
		if(loSaveFile.exists())
		{
			if(!loSaveFile.canWrite())
			{
				JOptionPane.showMessageDialog(null, "Save file is not writable. Please correct prior to exiting the appliciaton." , "File Error", JOptionPane.ERROR_MESSAGE);
			}
			lsFileName = asFileName;
		}
		else
		{
			if(ibDebug)
			{
				printLine("Current save file: " + asFileName);
			}
			String lsPath = asFileName.substring(0, asFileName.lastIndexOf(File.separator) + 1);
			if(ibDebug)
			{
				printLine("Path: " + lsPath);
			}
			File loDirectory = new File(lsPath);
			if(!loDirectory.exists())
			{
				if(ibDebug)
				{
					printLine(lsPath + " does not exist.");
				}
				
				if(!loDirectory.mkdirs())
				{
					JOptionPane.showMessageDialog(null, "Could not make requested directories." , "File Error", JOptionPane.ERROR_MESSAGE);
					ibExit = true;
					System.exit(-1);
				}
			}

			File loFile = new File(asFileName);
			try
			{
				if(loFile.createNewFile())
				{
					loFile.delete();
				}
			}
			catch(IOException e)
			{
				JOptionPane.showMessageDialog(null, "Save file cannot be created. Java exception: " + e, "File Error", JOptionPane.ERROR_MESSAGE);
				ibExit = true;
				System.exit(-1);
			}
			lsFileName = asFileName;
			
			if(ibDebug)
			{
				printLine("Save file name: " + lsFileName);
			}

		}
	}
	else
	{
		File loDirectory = new File(System.getenv("APPDATA") + File.separator + "JTClipper");
		if(!loDirectory.isDirectory())
		{
			if(!loDirectory.mkdir())
			{
				JOptionPane.showMessageDialog(null, "Save file directory cannot be created." , "File Error", JOptionPane.ERROR_MESSAGE);
				ibExit = true;
				System.exit(-1);
			}
		}
		
		lsFileName = System.getenv("APPDATA") + File.separator + "JTClipper" + File.separator + "JTClipperState.ser";
		File loFile = new File(lsFileName);
		
		if(!loFile.exists())
		{
			try
			{
				if(!loFile.createNewFile())
				{
					JOptionPane.showMessageDialog(null, "Save file cannot be created." , "File Error", JOptionPane.ERROR_MESSAGE);
					ibExit = true;
					System.exit(-1);
				}
				else
				{
					loFile.delete();
				}
			}
			catch(IOException e)
			{
				JOptionPane.showMessageDialog(null, "Error in save file. Java exception: " + e, "File Error", JOptionPane.ERROR_MESSAGE);
				ibExit = true;
				System.exit(-1);
			}
		}
	}
	
	return lsFileName;
}

public void saveState()
{
	FileOutputStream loFileOutStream;
	ObjectOutputStream loObjectOutStream;
	Vector ioContainer = new Vector(4);
	
	ioEncrypted = new Boolean(ibEncrypted);
	
	ioContainer.insertElementAt(ioPermCBVersions,0);
	ioContainer.insertElementAt(ioCBVersions,1);
	ioContainer.insertElementAt(ioEncrypted,2);
	if(ibEncrypted)
	{
		isPWCheck = this.encrypt("This is a password check.");
	}
	ioContainer.insertElementAt(isPWCheck,3);
	
	try
	{
		if(ibDebug)
		{
			printLine("Saving Objects:");
			printLine("CBVersions: " + ioCBVersions);
		}
		loFileOutStream = new FileOutputStream(isSaveFile);
		loObjectOutStream = new ObjectOutputStream(loFileOutStream);
		loObjectOutStream.writeObject(ioContainer);
		loObjectOutStream.close();
	}
	catch(Exception e)
	{
		printLine("Exception -  " + e);
		printLine(" StackTrace: ");
		e.printStackTrace(System.out);
		ibExit = true;
		System.exit(-1);	
	}	
}

public void loadState()
{
	FileInputStream loFileInStream = null;
	ObjectInputStream loObjectInStram = null;
	File loFile = null;
	Vector ioContainer = new Vector(4);
	
	String lsLoadError = "Saved file is corrupted. This should be corrected on exit. Java Exception: " ;
	
	try
	{
		loFile = new File(isSaveFile); 
		if(ibDebug)
		{
			printLine("Save File: " + isSaveFile);
			printLine("Save file exists? " + loFile.exists());
		}
		if(!loFile.exists())
		{
			JOptionPane.showMessageDialog(null, "No save file found. It will be created on exit." , "Load Error", JOptionPane.ERROR_MESSAGE);
			return;
		}
		loFileInStream = new FileInputStream(isSaveFile);
		loObjectInStram = new ObjectInputStream(loFileInStream);
		ioContainer = (Vector)loObjectInStram.readObject();
		loObjectInStram.close();
		ioPermCBVersions = (PDStack)ioContainer.elementAt(0);
		ioCBVersions = (PDStack)ioContainer.elementAt(1);
		ioEncrypted = (Boolean)ioContainer.elementAt(2);
		ibEncrypted = ioEncrypted.booleanValue();
		isPWCheck = (String)ioContainer.elementAt(3);
	}
	//I have seen it once where the save file had a zero length. I don't know how this happened, but this code should 
	//handle this issue if it happens again. 
	catch(EOFException e)
	{
		JOptionPane.showMessageDialog(null,  lsLoadError + e, "Load Error", JOptionPane.ERROR_MESSAGE);
		
		try
		{
			loObjectInStram.close();
		}
		catch(Exception e2) {}
		
		loFile.delete();
	}
	//This could happen if the file is changed after it is saved. 
	catch(StreamCorruptedException e)
	{
		JOptionPane.showMessageDialog(null, lsLoadError + e , "Load Error", JOptionPane.ERROR_MESSAGE);
		
		try
		{
			loObjectInStram.close();
		}
		catch(Exception e2) {}
		
		loFile.delete();
	}
	//This will be the case if the base saved object is ever changed to a new class.
	catch(ClassCastException e)
	{
		JOptionPane.showMessageDialog(null, "Saved file is invalid. Java Exception: " + e + ". This should be corrected on exit." , "Load Error", JOptionPane.ERROR_MESSAGE);
		
		try
		{
			loObjectInStram.close();
		}
		catch(Exception e2) {}
		
		loFile.delete();
	}
	
	//This may happen if the base saved object is ever changed.
	catch(InvalidClassException e)
	{
		if(ibDebug)
		{
			printLine("Java Exception: " + e );
		}
		JOptionPane.showMessageDialog(null, "Saved file is invalid. This should be corrected on exit." , "Load Error", JOptionPane.ERROR_MESSAGE);
		try
		{
			loObjectInStram.close();
		}
		catch(Exception e2) {}
		
		loFile.delete();
	}
	
	//This should never happen, but whay not test for it. This would be if this code
	//usese a different class then what was last saved.
	catch(ClassNotFoundException e)
	{
		JOptionPane.showMessageDialog(null,  lsLoadError + e, "Load Error", JOptionPane.ERROR_MESSAGE);
		loFile.delete();
		printLine("Exception - " + e);
		printLine("StackTrace: ");
		e.printStackTrace(System.out);
	}
	catch(Exception e)
	{
		printLine("Exception - ");
		printLine("StackTrace: ");
		e.printStackTrace(System.out);
		ibExit = true;
		System.exit(-1);
	}
	
	iiPDStackSize = ioCBVersions.getMax();
	iiMenuCount = ioCBVersions.getSize();
	
	if(ibDebug)
	{
		printLine("Max Size: " + iiPDStackSize);
		printLine("Current Size: " + iiMenuCount);
		printLine("Loading Objects:");
		printLine("CBVersions: " + ioCBVersions);
	}
	
	for(int i = (iiMenuCount - 1); i >= 0; i--)
	{
		String lsClipItem = this.getClipData(i);
		
		if(ibDebug)
		{
			printLine("Load State - Clip Data: " + lsClipItem);
		}

		//Calculate the MD5 Sum.
		String lsMD5 = DigestUtils.md5Hex(lsClipItem); 
		if(ibDebug)
		{
			printLine("MD5Sum: " + lsMD5);
		}

		ioCBVersionsMD5.push(lsMD5);
		lsClipItem = this.buildMenuText(lsClipItem);
		this.updateMenu(lsClipItem, false);
	}
	
	//This will only be a problem is I change the max in code and recompile and then
	//saved file is a samaller max. My Bad! Fix it.
	int liPermMax = ioPermCBVersions.getMax();
	if( liPermMax != iiPermMax)
	{
		ioPermCBVersions.setMax(iiPermMax);
	}
	
	iiPermCount = ioPermCBVersions.getSize();
	if(ibDebug)
	{
		printLine("Loaded Perm Count: " + iiPermCount);
	}
	if(iiPermCount > 0)
	{
//			for (int i = 0; i < iiPermCount; i++)
		for (int i = iiPermCount - 1; i >= 0; i--)
		{
			String lsClipItem = this.getClipData(i, true);
		
			if(ibDebug)
			{
				printLine("Loaded Perm Text: " + lsClipItem);
			}
	
			lsClipItem = this.buildMenuText(lsClipItem, iiMaxPermMenuItemSize);
			if(ibDebug)
			{
				printLine(" Loaded Perm Menu Text: " + lsClipItem);
			}
			
			this.updatePermMenu(lsClipItem, false);
		}
	}
//	this.buildMenu();
//	ioTrayIcon.setPopupMenu(ioPopupMenu);
}

private void updatePermMenu(String asClipItem, Boolean abBuildMenu)
{
	MenuItem loMenuItem = new MenuItem(asClipItem);
	loMenuItem.addActionListener(this);
	ioPermClipItem.push(loMenuItem);
	if(abBuildMenu)
	{
		this.buildMenu();
		ioTrayIcon.setPopupMenu(ioPopupMenu);
	}
}

private void buildMenu()
{
	if(ibDebug)
	{
		printLine("In build menu.");
	}
	String lsMenuLabel = "";
	ioPopupMenu.removeAll(); //Have to clear the menu as I want Exit on the bottom.
	if( 0 < iiMenuCount)
	{	
		for(int i = (iiMenuCount - 1); i >= 0; i--)
		{
			if(ibDebug)
			{
				printLine("Menu Item Number: " + i);
			}
			
			MenuItem loMenuItem = (MenuItem)ioClipItem.get(i); //4/4 Why null pointer on setting max?

			if(ibDebug)
			{
				printLine("Menu Item Number: " + loMenuItem);
			}
						
			lsMenuLabel = loMenuItem.getLabel();
			
			if(ibDebug)
			{
				printLine("Menu Item: " + lsMenuLabel);
			}
			
			int liParPos = lsMenuLabel.indexOf(')');
			lsMenuLabel = lsMenuLabel.substring(liParPos + 2);
			loMenuItem.setLabel("" + (i + 1) + ") " + lsMenuLabel);
			loMenuItem.setName("" + i);
			ioPopupMenu.add(loMenuItem);
		}
		ioPopupMenu.addSeparator();
	}
	
	if(iiImageCount > 0)
	{
		ioPopupMenu.add(ioImages);
		ioPopupMenu.addSeparator();
	}
	
	if(iiPermCount > 0)
	{
		ioPermItemsPopupMenu.removeAll(); //Need to rebuild the menu in case an item is removed.
		
		for(int i = 0, j = iiPermCount ; i < iiPermCount; j--, i++)
		{
			if(ibDebug)
			{
				printLine("Menu Item Number: " + i);
			}
			
			MenuItem loMenuItem = (MenuItem)ioPermClipItem.get(j -1);

			if(ibDebug)
			{
				printLine("Menu Item: " + loMenuItem);
			}
						
			lsMenuLabel = loMenuItem.getLabel();
			
			if(ibDebug)
			{
				printLine("Menu Label: " + lsMenuLabel);
			}

			int liParPos = lsMenuLabel.indexOf(')');
			lsMenuLabel = lsMenuLabel.substring(liParPos + 2);
			loMenuItem.setLabel("" + (i + 1) + ") " + lsMenuLabel);
			loMenuItem.setName("P" + (j - 1));
			ioPermItemsPopupMenu.add(loMenuItem);
		}
		ioPopupMenu.add(ioPermItemsPopupMenu);
		ioPopupMenu.add(ioRemovePerm);
	}

	if(iiMenuCount > 0)
	{
		ioPopupMenu.add(ioMakePerm);
		ioPopupMenu.addSeparator();
		ioPopupMenu.add(ioClearAll);
		ioPopupMenu.add(ioClearItem);
		ioPopupMenu.addSeparator();
	}
	ioPopupMenu.add(ioSetMaxItems);
// For when I finish image processing.
//	ioPopupMenu.add(ioCBImages);
	ioPopupMenu.add(ioCapture);
	ioPopupMenu.add(ioCBEncrypted);
	ioPopupMenu.addSeparator();
	ioPopupMenu.add(ioExit);
}

public void mouseClicked(MouseEvent e)
{
	if(ibDebug)
	{
		printLine("Button: " + e.getButton());
		printLine("ClickCount: " + e.getClickCount());
	}
	
	if(e.getButton()== 1 && e.getClickCount() == 1)
	{
		if(ibCapture)
		{
			if(ibConvert)
			{
				ioTrayIcon.setToolTip("Convert Off");
				ioTrayIcon.setImage(imageOff);
				ibConvert = false;
			}
			else
			{
				ioTrayIcon.setToolTip("Convert On");
				ioTrayIcon.setImage(imageOn);
				//Need to clear the clipboard so we don't get unexpected data.
				StringSelection selection = new StringSelection("");
				ioClipboard.setContents(selection, selection);
				ibConvert = true;
			}
		}
		else
		{
			ioTrayIcon.displayMessage("Convert", "Capture must be on to change convert.", TrayIcon.MessageType.INFO);
		}
	}
}

//Creates an inner class to allow for simulation of a string based switch statement.
private enum MenuItemNumber
{
	EXIT, CLEARALL, CLEARITEM, SETMAXITEMS, CAPTURE, MAKEITEMPERMANENT, REMOVEPERMANENTITEM, NOVALUE;
	
	public static MenuItemNumber toItemNum(String asItemName)
	{
		if(asItemName != null)
		{
			asItemName = asItemName.toUpperCase();
			try
			{
				return valueOf(asItemName);
			} 
			catch (Exception e)
			{
				return NOVALUE;
			}
		}
		else
		{
			return NOVALUE;
		}
	}   
}

//Creates an inner class to allow for simulation of a string based switch statement.
private enum CBMenuItemNumber
{
	CAPTURE, ENCRYPTED, NOVALUE;
	
	public static CBMenuItemNumber toItemNum(String asItemName)
	{
		if(asItemName != null)
		{
			asItemName = asItemName.toUpperCase();
			try
			{
				return valueOf(asItemName);
			} 
			catch (Exception e)
			{
				return NOVALUE;
			}
		}
		else
		{
			return NOVALUE;
		}
	}   
}

public void itemStateChanged(ItemEvent e)
{
	String lsMenuItem = null;
	CheckboxMenuItem loMenuItem = null;

	if(ibDebug)
	{
		printLine("In itemStateChanged");
		printLine("Item: " + e.getItem());
		printLine("Item Selectable: " + e.getItemSelectable());
		printLine("ItemStateChanged: " + e.getStateChange());
	}
	
	loMenuItem = (CheckboxMenuItem)e.getItemSelectable();
	lsMenuItem = loMenuItem.getName();

	if(ibDebug)
	{
		printLine("Item Name: " + lsMenuItem);
	}
	
	switch (CBMenuItemNumber.toItemNum(lsMenuItem))
	{
		case CAPTURE:
		{
			//1 = selected
			//2 = deslected
			if(e.getStateChange() == 1 )
			{
				//I thought about using a different icon for capture off, but since if capture is off, then convert is off.
				ibCapture = true;
				this.buildMenu();
				if(ibConvert)
				{
					ioTrayIcon.setToolTip("Convert On");
					ioTrayIcon.setImage(imageOn);
				}
				else
				{
					ioTrayIcon.setToolTip("Convert Off");
					ioTrayIcon.setImage(imageOff);
				}
				
				//Need to clear the clipboard so we don't get unexpected data.
				StringSelection selection = new StringSelection("");
				ioClipboard.setContents(selection, selection);
			}
			else
			{
				ibCapture = false;
				this.buildMenu();
				ioTrayIcon.setToolTip("Capture Off");
				ioTrayIcon.setImage(imageOff);	
			}
			break;
		}
		
		case ENCRYPTED:
		{
			//1 = selected
			//2 = deslected
			if(e.getStateChange() == 1 )
			{
				this.encryptAll(true);
			}
			else
			{
				this.encryptAll(false);
			}
			break;
		}
	}
}

public String getPassPhrase()
{
	String lsPassPhrase = null;
	String lsPassPhraseCheck = null;
	
	boolean lbInputInvalid = true;
	boolean lbFirstEntry = false;
	
	if(ibDebug)
	{
		printLine("In: getPassPhrase");
	}
	
	while(lbInputInvalid)
	{
		if(!lbFirstEntry)
		{
			lsPassPhrase = (String)JOptionPane.showInputDialog(null, "Please enter your password.", "Password", JOptionPane.QUESTION_MESSAGE, null, null, null);
			if(ibDebug)
			{
				printLine("Password: " + lsPassPhrase);
			}
			
			if( null == lsPassPhrase || lsPassPhrase.length() < 8)
			{
				if(ibDebug)
				{
					printLine("lsPassPhrase is null or blank");
				}
				JOptionPane.showMessageDialog(null, "Passwords must be at least 8 characters long." , "Input Error", JOptionPane.ERROR_MESSAGE);
			}
			else
			{
				lbFirstEntry = true;
			}
		}
		else
		{
			if(ibDebug)
			{
				printLine("Second prompt.");
			}
			lsPassPhraseCheck = (String)JOptionPane.showInputDialog(null, "Please reenter your password.", "Password", JOptionPane.QUESTION_MESSAGE, null, null, null);
			if(ibDebug)
			{
				printLine("Password: " + lsPassPhraseCheck);
			}
			
			if( null == lsPassPhraseCheck || lsPassPhraseCheck.length() == 0)
			{
				if(ibDebug)
				{
					printLine("lsPassPhraseCheck is null or blank");
				}
			}
			else
			{
				if(lsPassPhraseCheck.equals(lsPassPhrase))
				{
					lbInputInvalid = false;
				}
				else
				{
					JOptionPane.showMessageDialog(null, "Passwords do not match. Please try again." , "Input Error", JOptionPane.ERROR_MESSAGE);
					lbFirstEntry = false;
				}
			}
		}
	}
	
	return lsPassPhrase;
}

public void encryptAll (boolean abEncrypted)
{
	int liCount = 0;
	int liPermCount = 0;

	//Suspend convert and capture.
	boolean lbConvert = ibConvert;
	boolean lbCapture = ibCapture;
	ibConvert = false;
	ibCapture = false;
	
	if(ibDebug)
	{
		printLine("In: encryptAll");
		printLine("Encrypt: " + abEncrypted);
	}
	
	if(!abEncrypted)
	{
 		JOptionPane.showMessageDialog(null,"WARNING: Any sensitive data that was encrypted will now be easily accessed.", "Unencrupt", JOptionPane.WARNING_MESSAGE);
	}
	
	if(ioEncrypt == null || abEncrypted)
	{
		this.initEncryption(this.getPassPhrase());
	}
	
	liCount = ioCBVersions.getSize();
	liPermCount = ioPermCBVersions.getSize();
	
	//Copy clip data to temp PDStack and reset origianls.
	PDStack loClipData = new PDStack(liCount);
	for (int i=0; i < liCount; i++)
	{
		String lsClipData = (String)ioCBVersions.get(i);
		if(ibDebug)
		{
			printLine("Clip Data: " +  lsClipData);
		}
		loClipData.push(lsClipData);
	}
	ioCBVersions = new PDStack(iiPDStackSize);
	
	PDStack loPermClipData = new PDStack(liPermCount);
	for (int i=0; i < liPermCount; i++)
	{
		String lsClipData = (String)ioPermCBVersions.get(i);
		if(ibDebug)
		{
			printLine("Perm Clip Data: " +  lsClipData);
		}
		loPermClipData.push(lsClipData);
	}
	ioPermCBVersions = new PDStack(iiPermMax);
	
	//Copy clip data back to PDStack encrypting/decrypting as needed.
	for (int i=0; i < liCount; i++)
	{
		String lsClipData = null;
		lsClipData = (String)loClipData.get(i);
		
		if(ibDebug)
		{
			printLine("Temp Clip Data: " + i + ") " +  lsClipData);
		}
		
		if(abEncrypted)
		{
			lsClipData = this.encrypt(lsClipData);
		}
		else
		{
			try
			{	
				lsClipData = this.decrypt(lsClipData);
			}
			catch(javax.crypto.BadPaddingException e) 
			{
				printLine(e.toString());
				JOptionPane.showMessageDialog(null, "Decryption Error (Invalid Passowrd?), exiting." , "Decryption Error", JOptionPane.ERROR_MESSAGE);
				System.exit(1);
			}
		}
		ioCBVersions.push(lsClipData);
		
		if(ibDebug)
		{
			printLine("New Clip Data: " +  lsClipData);
		}
	}
	
	//Copy perm clip data back to PDStack encrypting/decrypting as needed.
	for (int i=0; i < liPermCount; i++)
	{
		String lsClipData = null;
		lsClipData = (String)loPermClipData.get(i);
		
		if(ibDebug)
		{
			printLine("Temp Perm Clip Data: " + i + ") " +  lsClipData);
		}
		
		if(abEncrypted)
		{
			lsClipData = this.encrypt(lsClipData);
		}
		else
		{
			try
			{
				lsClipData = this.decrypt(lsClipData);
			}
			catch(javax.crypto.BadPaddingException e) 
			{
				printLine(e.toString());
				JOptionPane.showMessageDialog(null, "Decryption Error (Invalid Passowrd?), exiting." , "Decryption Error", JOptionPane.ERROR_MESSAGE);
				System.exit(1);
			}
		}
		ioPermCBVersions.push(lsClipData);
	}

	ibEncrypted = abEncrypted;
	this.saveState();
	//Restore convert and capture.
	ibConvert = lbConvert;
	ibCapture = lbCapture;
}

public String getClipData(int aiItem)
{
	return getClipData(aiItem, false);
}

public String getClipData(int aiItem, boolean abPerm)
{
	String lsClipData = null;
	
	if(abPerm)
	{
		lsClipData = (String)ioPermCBVersions.get(aiItem);
	}
	else
	{
		lsClipData = (String)ioCBVersions.get(aiItem);
	}
	
	if(ibDebug)
	{
		printLine("In: getClipData");
		printLine("Perm: " + abPerm);
		printLine("Item: " + aiItem);
		printLine("Clip Data:" + lsClipData);
	}
	
	if(ibEncrypted)
	{
		if(ioEncrypt == null && !ibPWChecked)
		{
			boolean lbPassPhraseValid = false;
			while( !lbPassPhraseValid)
			{
				this.initEncryption(this.getPassPhrase());
				String lsTestPP = null;
				try
				{
					lsTestPP = this.decrypt(isPWCheck);
				}
				catch(javax.crypto.BadPaddingException e) 
				{
					printLine(e.toString());
					JOptionPane.showMessageDialog(null, "Decryption Error (Invalid Passowrd?), exiting." , "Decryption Error", JOptionPane.ERROR_MESSAGE);
					System.exit(1);
				}
				
				if(!(lsTestPP == null) && lsTestPP.equals("This is a password check."))
				{
					ibPWChecked = true;
					lbPassPhraseValid = true;
				}
				else
				{
					JOptionPane.showMessageDialog(null, "Password is not correct. Exiting." , "Password Error", JOptionPane.ERROR_MESSAGE); 
					System.exit(1);
				}
			}
		}
		
		try
		{
			lsClipData = this.decrypt(lsClipData);
		}
		catch(javax.crypto.BadPaddingException e) 
		{
			printLine(e.toString());
			JOptionPane.showMessageDialog(null, "Decryption Error (Invalid Passowrd?), exiting." , "Decryption Error", JOptionPane.ERROR_MESSAGE);
			System.exit(1);
		}
		
		if(ibDebug)
		{
			printLine("Clip Data After Dycrypt:" + lsClipData);
		}
	}
	
	return lsClipData;
	
}

public void pushClipData(String asInput)
{
	pushClipData(asInput, false);
}

public void pushClipData(String asInput, boolean abPerm)
{
	if(ibDebug)
	{
		printLine("In: pushClipData");
		printLine("Perm: " + abPerm);
		printLine("Clip Data:" + asInput);
	}
	
	if(ibEncrypted)
	{
		if(ioEncrypt == null)
		{
			this.initEncryption(this.getPassPhrase());
		}
		
		asInput = this.encrypt(asInput);
		if(ibDebug)
		{
			printLine("Encrypted Clip Data:" + asInput);
		}
	}
	
	if(abPerm)
	{
		ioPermCBVersions.push(asInput);
	}
	else
	{
		ioCBVersions.push(asInput);
	}
}

public void actionPerformed(ActionEvent e)
{
	if(e.getActionCommand() != null)
	{
		if(ibDebug)
		{
			printLine("Action Command: " + e.getActionCommand());
			printLine("Action Source: " + e.getSource());
		}
		
		MenuItem loMenuItem = (MenuItem)e.getSource(); 
		String lsName = loMenuItem.getName();
		
		if(ibDebug)
		{
			printLine("Menu Item Name: " + lsName);
		}
		
		if(lsName.equals("Exit") || lsName.equals("ClearItem") ||
			lsName.equals("ClearAll") || lsName.equals("SetMaxItems") ||
			lsName.equals("Capture") || lsName.equals("MakeItemPermanent") ||
			lsName.equals("RemovePermanentItem"))
		{
			switch (MenuItemNumber.toItemNum(lsName))
			{
				case EXIT:
				{
					if(ibDebug)
					{
						printLine("Exiting...");
					}
					ibExit = true;
					System.exit(0);
					break;
				}
				
				case CLEARALL:
				{
					for(int i = iiMenuCount - 1; i >= 0; i--)
					{
						this.clearItem(i);
					}
					iiMenuCount = 0;
					//Need to claer the clipboard to keep the current data from being pushed back on the stack.
					StringSelection selection = new StringSelection("");
					ioClipboard.setContents(selection, selection);
					this.buildMenu();
					this.saveState();
					break;
				}
				
				case CLEARITEM:
				{
					if(ibDebug)
					{
						printLine("In Clear Item");
					}
					this.clearItem();
					this.buildMenu();
					this.saveState();
					break;
				}
				
				case SETMAXITEMS:
				{
					int liMax = this.setMaxMenuItems();
					if(ibDebug)
					{
						printLine("Max: " + liMax);
					}
					iiPDStackSize = liMax;
					ioCBVersions.setMax(iiPDStackSize);
					ioClipItem.setMax(iiPDStackSize);
					this.saveState();
					iiMenuCount = ioCBVersions.getSize();
					if(ibDebug)
					{
						printLine("Menu Count: " + iiMenuCount);
					}
					this.buildMenu();
					break;
				}
				
				case MAKEITEMPERMANENT:
				{
					if( iiPermCount == iiPermMax)
					{
						JOptionPane.showMessageDialog(null, "All permanent items are in use." , "Input Error", JOptionPane.ERROR_MESSAGE); 
					}
					else
					{
						this.makeItemPerm();
						this.saveState();
					}
					break;
				}
				
				case REMOVEPERMANENTITEM:
				{
					this.removePermItem();
					this.buildMenu();
					this.saveState();
					break;
				}	
			}
		}
		else
		{
			boolean lbPerm = false;
			if(lsName.startsWith("P"))
			{
				lbPerm = true;
				lsName = lsName.substring(1);
			}
			int i = Integer.parseInt(lsName);
			String lsClipData = "";
			
			if(lbPerm)
			{
				//i = iiPermCount - i;
				lsClipData = this.getClipData(i, true);
			}
			else
			{
				lsClipData = this.getClipData(i);
			}
			
			if(ibDebug)
			{
				printLine("Number: " + i);
				printLine("Clip Data: " + lsClipData);
			}
			
			iiClipCurrent = i;
				
			StringSelection selection = new StringSelection(lsClipData);
			ioClipboard.setContents(selection, selection);
				
			if(ibDebug)
			{
				printLine("Data: " + lsClipData);
			}
		}
	}
	else
	{
		if(ibDebug)
		{
			printLine("Null Action");
		}
	}
}

private void makeItemPerm()
{
	int liItem = 0;
	boolean lbInputInvalid = true;
	
	if(ibDebug)
	{
		printLine("In: makeItemPerm");
	}
	
	while(lbInputInvalid)
	{
		String lsMsg = "Please enter the item,  between 1 and " + iiMenuCount + ", to make permanent.";
		if(!ibEncrypted)
		{
			lsMsg = lsMsg + "\n WARNING: Do not store sensitive data as permanent. The data NOT encrypted and can be easily accessed.";
		}
		String lsItem = (String)JOptionPane.showInputDialog(null,lsMsg, "Make Item Permanent ", JOptionPane.QUESTION_MESSAGE, null, null, null);
		if(ibDebug)
		{
			printLine("Item Number as String: " + lsItem);
		}
		
		if( null == lsItem )
		{
			lbInputInvalid = false;
			if(ibDebug)
			{
				printLine("lsItem is null");
			}
		}
		else
		{
			try
			{
				if( null != lsItem)
				{
					liItem = Integer.parseInt(lsItem);
				}
				
				if(liItem < 1 || liItem > iiMenuCount)
				{
					JOptionPane.showMessageDialog(null, "Please a number between 1 and " + iiMenuCount + "." , "Input Error", JOptionPane.ERROR_MESSAGE);
				}
				else
				{
					lbInputInvalid = false;
				}
			}
			catch (NumberFormatException e)
			{
				JOptionPane.showMessageDialog(null, "Please enter whole numbers only." , "Input Error", JOptionPane.ERROR_MESSAGE); 
			}
				
			if(ibDebug)
			{
				printLine("Item Number: " + liItem);
			}
		}
	}
	
	if(liItem > 0)
	{
		String lsClipData = this.getClipData(liItem -1);
		
		if(ibDebug)
		{
			printLine("Item Text: " + lsClipData);
		}
		
		String lsClipDataMD5 = (String)ioCBVersionsMD5.get(liItem -1);
		if(ibDebug)
		{
			printLine("MD5 Text: " + lsClipDataMD5);
		}
		
		//See if this is a duplicate item. If so,don't add it.
		boolean lbDup = false;
		
		for (int i=0; i < iiPermCount; i++)
		{
			String lsPermText = this.getClipData(i , true);
			String lsMD5Item = DigestUtils.md5Hex(lsPermText);
			
			if(ibDebug)
			{
				printLine("Perm Text: " + lsPermText);
				printLine("Perm Item MD5: " + lsMD5Item);
			}
			
			if(lsClipDataMD5.equals(lsMD5Item))
			{
				lbDup = true;
				break;
			}
		}
		if(lbDup)
		{
			JOptionPane.showMessageDialog(null, "The request item is already a permanent item." , "Duplicate Permanent", JOptionPane.INFORMATION_MESSAGE);
		}
		else
		{
			iiPermCount++;
			this.pushClipData(lsClipData, true);
			String lsMenuText = this.buildMenuText(lsClipData, iiMaxPermMenuItemSize);
			this.updatePermMenu(lsMenuText, true);
		}
	}
}

private void updateMenu(String asClipData, boolean abBuildMenu)
{
	MenuItem loMenuItem = new MenuItem(asClipData);
	loMenuItem.addActionListener(this);
	ioClipItem.push(loMenuItem);
	iiMenuCount = ioClipItem.getSize();
	if(abBuildMenu)
	{
		this.buildMenu();
		ioTrayIcon.setPopupMenu(ioPopupMenu);
	}
}

private void removePermItem()
{
	int liItem = 0;
	boolean lbInputInvalid = true;
	
	while(lbInputInvalid)
	{
		String lsItem = (String)JOptionPane.showInputDialog(null,"Please enter the item number, between 1 and " + iiPermCount + ", of the permanent item to remove.", 
			"Remove Permanent Item", JOptionPane.QUESTION_MESSAGE, null, null, null);
		if(ibDebug)
		{
			printLine("Item Number as String: " + lsItem);
		}
		
		if( null == lsItem )
		{
			lbInputInvalid = false;
			if(ibDebug)
			{
				printLine("lsItem is null");
			}
		}
		else
		{
			try
			{
				if( null != lsItem)
				{
					liItem = Integer.parseInt(lsItem);
				}
				if(liItem < 1 || liItem > iiPermCount)
				{
					JOptionPane.showMessageDialog(null, "Please a number between 1 and " + iiPermCount + "." , "Input Error", JOptionPane.ERROR_MESSAGE);
				}
				else
				{
					lbInputInvalid = false;
				}
			}
			catch (NumberFormatException e)
			{
				JOptionPane.showMessageDialog(null, "Please enter whole numbers only." , "Input Error", JOptionPane.ERROR_MESSAGE); 
			}
				
			if(ibDebug)
			{
				printLine("Item Number: " + liItem);
			}
		}
	}
	
	if(liItem > 0)
	{
		
		ioPermCBVersions.remove(iiPermCount - liItem);
		ioPermClipItem.remove(iiPermCount - liItem);

		iiPermCount--;
	}
}

private void clearItem(int aiItem)
{
	ioCBVersions.remove(aiItem);
	ioClipItem.remove(aiItem);
	ioCBVersionsMD5.remove(aiItem);
	iiMenuCount--;
}

private void clearItem()
{
	int liItem = 0;
	boolean lbInputInvalid = true;
	
	while(lbInputInvalid)
	{
		String lsItem = (String)JOptionPane.showInputDialog(null,"Please enter the max number, between 1 and " + iiMenuCount + ", of item to clear.", 
			"Item To Clear", JOptionPane.QUESTION_MESSAGE, null, null, null);
		if(ibDebug)
		{
			printLine("Item Number as String: " + lsItem);
		}
		
		if( null == lsItem )
		{
			lbInputInvalid = false;
			if(ibDebug)
			{
				printLine("lsItem is null");
			}
		}
		else
		{
			try
			{
				if( null != lsItem)
				{
					liItem = Integer.parseInt(lsItem);
				}
				if(liItem < 1 || liItem > iiMenuCount)
				{
					JOptionPane.showMessageDialog(null, "Please a number between 1 and " + iiMenuCount + "." , "Input Error", JOptionPane.ERROR_MESSAGE);
				}
				else
				{
					lbInputInvalid = false;
				}
			}
			catch (NumberFormatException e)
			{
				JOptionPane.showMessageDialog(null, "Please enter whole numbers only." , "Input Error", JOptionPane.ERROR_MESSAGE); 
			}
				
			if(ibDebug)
			{
				printLine("Item Number: " + liItem);
			}
			liItem--;
			iiMenuCount--;
			if(liItem == iiClipCurrent)
			{
				iiClipCurrent = 0;
					StringSelection selection = new StringSelection("");
					ioClipboard.setContents(selection, selection);
			}
			
			ioCBVersions.remove(liItem);
			ioClipItem.remove(liItem);
			ioCBVersionsMD5.remove(liItem);
			
			if(0 == liItem )
			{
				try
				{
					String lsClipData = (String)ioClipboard.getData(DataFlavor.stringFlavor); //Throws exception if no string. This is OK.
					String lsNewClipData = this.getClipData(0);
					StringSelection selection = new StringSelection(lsNewClipData);
					ioClipboard.setContents(selection, selection);
				}
				catch(UnsupportedFlavorException e)
				{
					if(ibDebug)
					{
						printLine("No string on clipboard.");
					}
				}
				catch(Exception e) {}
			}
		}
	}
}

private int setMaxMenuItems()
{
	int liMax = iiPDStackSize;
	boolean lbInputInvalid = true;
	
	while(lbInputInvalid)
	{
		String lsMax = (String)JOptionPane.showInputDialog(null,"Please enter the max number, between 1 and 30, of items to keep.", 
			"Number of Items", JOptionPane.QUESTION_MESSAGE, null, null, iiPDStackSize);
		try
		{
			if( null != lsMax)
			{
				liMax = Integer.parseInt(lsMax);
			}
			if(liMax < 1 || liMax > 30)
			{
				JOptionPane.showMessageDialog(null, "Please a number between 1 and 30." , "Input Error", JOptionPane.ERROR_MESSAGE);
			}
			else
			{
				lbInputInvalid = false;
			}
		}
		catch (NumberFormatException e)
		{
			JOptionPane.showMessageDialog(null, "Please enter whole numbers only." , "Input Error", JOptionPane.ERROR_MESSAGE); 
		}
	}
	return liMax;
}
public String encrypt(String asInput)
{
	String lsEncoded = null;
	byte[] lbtUTF8 = null;
	byte[] lbtEncrypt = null;
	
	try
	{
		// Encode the string into bytes using utf-8
		lbtUTF8 = asInput.getBytes("UTF8");

		// Encrypt
		lbtEncrypt = ioEncrypt.doFinal(lbtUTF8);

		//lsEncoded = new String(lbtEncrypt);
		//lsEncoded = new sun.misc.BASE64Encoder().encode(lbtEncrypt);
		lsEncoded = Base64.encodeBytes(lbtEncrypt);
	}
	catch (javax.crypto.BadPaddingException e)
	{
		printLine("Exception: " + e);
	}
	catch (IllegalBlockSizeException e)
	{
		printLine("Exception: " + e);
	}
	catch (UnsupportedEncodingException e)
	{
		printLine("Exception: " + e);
	}
	return lsEncoded;
}

public String decrypt(String asInput) throws javax.crypto.BadPaddingException
{
	String lsRtc = null;
	byte[] lbtUTF8 = null;
	byte[] lbtDecrypt = null;
	
	try
	{
		//lbtDecrypt = asInput.getBytes();
		//lbtDecrypt = new sun.misc.BASE64Decoder().decodeBuffer(asInput);
		lbtDecrypt = Base64.decode(asInput);
		
		// Decrypt
		lbtUTF8 = ioDecrypt.doFinal(lbtDecrypt);

		// Decode using utf-8
		lsRtc = new String(lbtUTF8, "UTF8");
	} 
	catch (javax.crypto.BadPaddingException e)
	{
		printLine("Exception: " + e);
	}
	catch (IllegalBlockSizeException e)
	{
		printLine("Exception: " + e);
	}
	catch (UnsupportedEncodingException e)
	{
		printLine("Exception: " + e);
	}
	catch (java.io.IOException e)
	{
		printLine("Exception: " + e);
	}
	return lsRtc;
}
} //End of class.

