package org.holmes.plugin.views;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.TimeUnit;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
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
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.browser.IWebBrowser;
import org.eclipse.ui.browser.IWorkbenchBrowserSupport;
import org.eclipse.ui.part.ViewPart;

public class HolmesView extends ViewPart {
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
	
	public HolmesView() {
		
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
		IEditorPart editor = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().getActiveEditor();
		String projectName="";
		
		IEditorInput input = editor.getEditorInput();
		IFile file = ((IFileEditorInput)input).getFile();
		
		String projectDirectory = file.getProject().getRawLocation().toPortableString();
		
		File workingDirectory = new File (projectDirectory.substring(0, projectDirectory.indexOf(file.getProject().getName())));
		
		projectName = file.getProject().getName();
		
				
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
		 
		
		File originalOutput = new File(workingDirectory.getPath()+"/holmes-output-original.txt");
		File passingOutput = new File(workingDirectory.getPath() + "/holmes-output-passing.txt");
		File failingOutput = new File(workingDirectory.getPath() + "/holmes-output-failing.txt");
		
		if (originalOutput.exists()) {
			try {
				BufferedReader br = new BufferedReader(new FileReader(originalOutput));
				
				String line = null;
				StringBuilder sb = new StringBuilder();
				
				while ((line=br.readLine())!=null) {
					sb.append(line);
					sb.append("<br>");
				}
				
				String originalContents = sb.toString();
				
				String oTest = originalContents.substring(originalContents.indexOf("O:")+2);
//				String oTrace = originalContents.substring(originalContents.indexOf("T:")+2);
				
				html.append("<h2> Original Failing Test</h2>");
				html.append("<font face='Monaco' size='2'>"+oTest+"</font>");
//				html.append("<br>");
//				html.append("<button onclick=\"myFunction()\">See Execution Trace</button>");
//				html.append("<div id=\"original\" style=\"display:none\">\n");
//				html.append(oTrace);
//				html.append("</div>");
				
				html.append("<br>");
				
				html.append("<script>\n" + 
						"function myFunction() {\n" + 
						"    var x = document.getElementById(\"original\");\n" + 
						"    if (x.style.display === \"none\") {\n" + 
						"        x.style.display = \"block\";\n" + 
						"    } else {\n" + 
						"        x.style.display = \"none\";\n" + 
						"    }\n" + 
						"}\n" + 
						"</script>");
				
				
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
		}
		
		if (passingOutput.exists()) {
			try {
				BufferedReader br = new BufferedReader(new FileReader(passingOutput));
				
				String line = null;
				StringBuffer sb = new StringBuffer();
				
				while ((line=br.readLine()) != null) {
					sb.append(line);
					sb.append("<br>");
				}
				
				String passingTests = sb.toString();
				
				int lastIndexPassing = 0;
				String findStr  = "P:";
				String findStrT = "T:";
				int lastIndexTrace = 0;
				String test = "";
				String trace = "";
				int count = 0;
				
				html.append("<h2>Passing Tests</h2>");
				while (lastIndexPassing != -1) {
					lastIndexPassing = passingTests.indexOf(findStr, lastIndexPassing);
//					lastIndexTrace = passingTests.indexOf(findStrT, lastIndexTrace);
					
					
					if (lastIndexPassing != -1) {
						test = passingTests.substring(lastIndexPassing+2);
						html.append("<font face='Monaco' size='2'>" +test+"</font>");
//						html.append("<br>");
						
						lastIndexPassing += test.length();
						
						int nextIndex = passingTests.indexOf(findStr, lastIndexPassing);
						
//						// Process differently if only one passing test
//						if (nextIndex == -1) {
//							trace = passingTests.substring(lastIndexTrace+2, passingTests.length());
//						} else {
//							trace = passingTests.substring(lastIndexTrace+2, nextIndex);						
//						}
						
//						lastIndexTrace += findStrT.length();
						
//						html.append("<button onclick=\"myFunction"+count+"()\">See Execution Trace</button>");
//						html.append("<div id=\"myDIV"+ count +"\" style=\"display:none\">\n");
//						html.append(trace);
//						html.append("</div>");
						
						html.append("<br>");
						
						html.append("<script>\n" + 
								"function myFunction"+count+"() {\n" + 
								"    var x = document.getElementById(\"myDIV"+ count
								+ "\");\n" + 
								"    if (x.style.display === \"none\") {\n" + 
								"        x.style.display = \"block\";\n" + 
								"    } else {\n" + 
								"        x.style.display = \"none\";\n" + 
								"    }\n" + 
								"}\n" + 
								"</script>");
						count ++;
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
				StringBuffer sb = new StringBuffer();
				
				while ((line=br.readLine()) != null) {
					sb.append(line);
					sb.append("<br>");
				}
				
				String failingTests = sb.toString();
				
				html.append("<h2>Additional Failing Tests</h2>");
				
				int lastIndexFailing = 0;
				String findStr  = "F:";
				String findStrT = "T:";
				int lastIndexTrace = 0;
				String test = "";
				String trace = "";
				int count = 5;
				
				while (lastIndexFailing != -1) {
					lastIndexFailing = failingTests.indexOf(findStr, lastIndexFailing);
//					lastIndexTrace = failingTests.indexOf(findStrT, lastIndexTrace);
					
					
					if (lastIndexFailing != -1) {
						test = failingTests.substring(lastIndexFailing+2);
						html.append("<font face='Monaco' size='2'>" +test+"</font>");
//						html.append("<br>");
						
						lastIndexFailing += test.length();
						
						int nextIndex = failingTests.indexOf(findStr, lastIndexFailing);
						
						// Process differently if only one passing test
//						if (nextIndex == -1) {
//							trace = failingTests.substring(lastIndexTrace+2, failingTests.length());
//						} else {
//							trace = failingTests.substring(lastIndexTrace+2, nextIndex);						
//						}
//																		
//						lastIndexTrace += findStrT.length();
						
//						html.append("<button onclick=\"myFunction"+count+"()\">See Execution Trace</button>");
//						html.append("<div id=\"myDIV"+ count +"\" style=\"display:none\">\n");
//						html.append(trace);
//						html.append("</div>");
						
						html.append("<br>");
						
						html.append("<script>\n" + 
								"function myFunction"+count+"() {\n" + 
								"    var x = document.getElementById(\"myDIV" + count
								+ "\");\n" + 
								"    if (x.style.display === \"none\") {\n" + 
								"        x.style.display = \"block\";\n" + 
								"    } else {\n" + 
								"        x.style.display = \"none\";\n" + 
								"    }\n" + 
								"}\n" + 
								"</script>");
						
						count ++;
					}
					
					
					
				}
				
								
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
