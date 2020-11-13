/**
 Created on July 10, 2007
  
  @author Stan Green
	
	This class implements a type of stack that pushes down one item at a time. The stack has a max size.
	When an item is put on the stack that would violate the max, the object on the bottom of the stack is 
	deleted. The deleted item is returnd from the push method. An object can be retrieved base on the array postion
	in the stack.
*/

import java.util.*;
import java.io.*;

public class PDStack implements Serializable
{
	private static final long serialVersionUID = 3424907626762634404L;
	private Vector<Object> ioVector;
	private int iiMax;

	PDStack()
	{
		iiMax = 30;
		ioVector = new Vector<Object>(iiMax);
	}
	
	PDStack(int aiMax)
	{
		iiMax = aiMax;
		ioVector = new Vector<Object>(iiMax);
	}
	
	public void remove(int aiItem)
	{
		ioVector.remove(aiItem);
	}
	
	public void setMax(int aiMax)
	{
		if(iiMax > aiMax) //Making the array smaller.
		{
			int liVectorSize = ioVector.size();
			if(liVectorSize > aiMax)
			{
				for(int i = liVectorSize -1; i >= aiMax; i--)
				{
					ioVector.remove(i);
				}
				iiMax = aiMax;
			}
			else
			{
				iiMax = aiMax;
			}
				
		}
		else
		{
			iiMax = aiMax;
		}
	}
	
	public int getMax()
	{
		return iiMax;
	}
	
	public int getSize()
	{
		return ioVector.size();
	}
	
	public Object push(Object aObject)
	{
		Object loObject = null;
		if(ioVector.size() == iiMax)
		{
			loObject = ioVector.elementAt(iiMax - 1);
			ioVector.remove(iiMax - 1);
		}
		ioVector.insertElementAt(aObject,0);
		
		return loObject;
	}
	
	public Object get(int aiPostion)
	{
		Object loObject = null;
		if(aiPostion + 1 <= ioVector.size())
		{
			 loObject = ioVector.elementAt(aiPostion);
		}
		return loObject;
	}
}
