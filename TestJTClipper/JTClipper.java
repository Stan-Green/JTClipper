public class JTClipper
{
	
native void DLLInit();

public void clipboardChange()
{
	System.out.println("ClipboardChange Triggered");
}	
	
public static void main ( String[] asArgs )
{
	JTClipper loJTClipper = new JTClipper(asArgs);
}

public JTClipper(String[] asArgs)
{
	try
	{
		System.loadLibrary("WatchClipboard");
		System.out.println("Loaded DLL");
	}
	catch (UnsatisfiedLinkError e)
	{
		System.out.println("DLL Not Found");
	}
	
	//Init the DLL
	DLLInit();
	
	while (true)
	{
		try
		{
			Thread.sleep(500);
		}
		catch(Exception e)
		{
			System.out.println("Exception - " + e);
			System.out.println("StackTrace: ");
			e.printStackTrace(System.out);
		}
	}
}
}
