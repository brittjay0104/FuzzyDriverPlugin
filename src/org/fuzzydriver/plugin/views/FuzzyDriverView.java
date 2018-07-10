package org.fuzzydriver.plugin.views;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.TimeUnit;

import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTError;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.LocationEvent;
import org.eclipse.swt.browser.LocationListener;
import org.eclipse.swt.browser.OpenWindowListener;
import org.eclipse.swt.browser.WindowEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.browser.IWebBrowser;
import org.eclipse.ui.browser.IWorkbenchBrowserSupport;
import org.eclipse.ui.part.ViewPart;

public class FuzzyDriverView extends ViewPart {
	Composite composite;
	
	Browser browser;
    String browserId;
    volatile boolean allowUrlChange;
    
	public static class ViewLabelProvider extends LabelProvider implements ITableLabelProvider {
		public String getColumnText(Object obj, int index) {
			return getText(obj);
		}
		public Image getColumnImage(Object obj, int index) {
			return getImage(obj);
		}
		public Image getImage(Object obj) {
			return PlatformUI.getWorkbench().getSharedImages().getImage(ISharedImages.IMG_OBJ_ELEMENT);
		}
	}
	
	public FuzzyDriverView() {
		
	}
	
	public void createPartControl(Composite parent) {
		composite = parent;
		
		updateView();
		
	}
	
	private void openBrowserInEditor(LocationEvent event) {
        URL url;
        try {
            url = new URL(event.location);
        } catch (MalformedURLException ignored) {
            return;
        }
        IWorkbenchBrowserSupport support = PlatformUI.getWorkbench().getBrowserSupport();
        try {
            IWebBrowser newBrowser = support.createBrowser(browserId);
            browserId = newBrowser.getId();
            newBrowser.openURL(url);
            return;
        } catch (PartInitException e) {
        		e.printStackTrace();
        }
    }
	
	public void updateView() {
		StringBuffer html = new StringBuffer();
		
		html.append("<head>");
		html.append("<link rel =\"stylesheet\" ");
		html.append("href=\"https://code.jquery.com/mobile/1.4.5/jquery.mobile-1.4.5.min.css\">");
		html.append("<link href=\"/css/livepreview-demo.css\" rel=\"stylesheet\" type=\"text/css\">");
		html.append("<script src=\"https://ajax.googleapis.com/ajax/libs/jquery/3.1.1/jquery.min.js\"></script>");
		html.append("<script src=\"https://code.jquery.com/jquery-1.11.3.min.js\"></script>");
		html.append("<script src=\"https://code.jquery.com/mobile/1.4.5/jquery.mobile-1.4.5.min.js\"></script>");
		html.append("<script type=\"text/javascript\" src=\"/js/jquery-live-preview.js\"></script>");
		html.append("</head>");
		
		 html.append("<body style=\"background-color:white;\"><hr>");
		 
		
		 File originalOutput = new File("/Users/bjohnson/eclipse-workspace/fuzzy-output-original.txt");
		File passingOutput = new File("/Users/bjohnson/eclipse-workspace/fuzzy-output-passing.txt");
		File failingOutput = new File("/Users/bjohnson/eclipse-workspace/fuzzy-output-failing.txt");
		
		if (originalOutput.exists()) {
			try {
				BufferedReader br = new BufferedReader(new FileReader(originalOutput));
				
				String line = null;
				
				html.append("<h2> Original Failing Test</h2>");
				while ((line=br.readLine()) != null) {
					if (line.startsWith("O:")){
						html.append("<font face='Monaco' size='2'>"+line.substring(line.indexOf("O:")+2, line.length())+"</font>");
						html.append("<br>");
					} else {
						html.append("<font face='Monaco' size='2'>"+line+"</font>");
						html.append("<br>");
					}
				}
				
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
		}
		
		if (passingOutput.exists()) {
			try {
				BufferedReader br = new BufferedReader(new FileReader(passingOutput));
				
				String line = null;
				
				html.append("<h2>Passing Tests</h2>");
					
				while ((line=br.readLine()) != null) {
					if (line.startsWith("P:")) {
						html.append("<font face='Monaco' size='2'>" +line.substring(line.indexOf("P:")+2, line.length())+"</font>");
						html.append("<br>");
					} else {
						html.append("<font face='Monaco' size='2'>"+line+"</font>");
						html.append("<br>");
					}
				}
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		if (failingOutput.exists()) {
			try {
				BufferedReader br = new BufferedReader(new FileReader(failingOutput));
				
				String line = null;
				
				html.append("<h2>Failing Tests</h2>");
				while ((line = br.readLine()) != null) {
					
					if (line.startsWith("F:")) {
						html.append("<font face='Monaco' size='2'>"+line.substring(line.indexOf("F:")+2, line.length())+"</font>");
						html.append("<br>");
					} else {
						html.append("<font face='Monaco' size='2'>"+line+"</font>");
						html.append("<br>");
					}
				}
				
//				setViewText("Found output file!");
				
				GridData data = new GridData(GridData.FILL_BOTH);
			    data.grabExcessHorizontalSpace = true;
			    data.grabExcessVerticalSpace = true;
			    try {
		           browser = new Browser(composite, SWT.NO_BACKGROUND);
		           browser.setLayoutData(data);
		           browser.setBackground(composite.getBackground());
		           browser.addOpenWindowListener(new OpenWindowListener() {
					
					@Override
					public void open(WindowEvent event) {
						event.required = true; // Cancel opening of new windows				
					}
				}); 
		           
		           browser.addLocationListener(new LocationListener() {
					
		    	       @Override
		    	       public void changing(LocationEvent event) {
		    	           // fix for SWT code on Won32 platform: it uses "about:blank"
		    	           // before
		    	           // set any non-null url. We ignore this url
		    	           if (allowUrlChange || "about:blank".equals(event.location)) {
		    	               return;
		    	           }
		    	           // disallow changing of property view content
		    	           event.doit = false;
		    	           // for any external url clicked by user we should leave
		    	           // property view
		    	           openBrowserInEditor(event);
		    	       }
					@Override
					public void changed(LocationEvent event) {
						// TODO Auto-generated method stub
						
					}
				}); 

		       } catch (SWTError e) {
		           System.out.println("Could not create org.eclipse.swt.widgets.Composite.Browser");
		       }
			    
			   String onReady = "$(document).ready(function() {  \n $(\".livepreview\").livePreview(); \n});";
			   boolean result = browser.execute(onReady);
			   
			   if (!result){
				   System.out.println(onReady);
			   }
			   
		       browser.setText(html.toString());
		       
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

	}
	
	public Composite getParent() {
		return composite;
	}
	public void setFocus() {
	}
}
