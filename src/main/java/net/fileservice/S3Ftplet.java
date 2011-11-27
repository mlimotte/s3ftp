package net.fileservice;

import org.apache.ftpserver.ftplet.*;

import java.io.*;


/**
 * @Author mlimotte
 * Date: 4/12/11
 */
public class S3Ftplet extends DefaultFtplet {

  private String foo;

  public void setFoo(String foo) {
    this.foo = foo;
  }

  public FtpletResult onDownloadStart(FtpSession session, FtpRequest request)
      throws FtpException, IOException {

        System.out.println("RCVD onDownloadStart");

        String requestedFile = request.getArgument();
    System.out.println("RCVD onDownloadStart B " + requestedFile);

        // get input stream from database - BLOB data
        InputStream in;
        try {
            in = getInputStream(requestedFile);
            System.out.println("RCVD onDownloadStart C");
            System.out.println("RCVD onDownloadStart D " + in);
        } catch (FileNotFoundException ex) {
            session.write(new DefaultFtpReply(550, "Cannot find data " + requestedFile));
            return FtpletResult.SKIP;
        }
    System.out.println("RCVD onDownloadStart 2");

        // transfer data
        try {
            // open data connection
            DataConnection out = null;
            session.write(new DefaultFtpReply(150, "Getting data connection."));
    System.out.println("RCVD onDownloadStart 3");
            try {
                out = session.getDataConnection().openConnection();
            } catch (Exception e) {
              e.printStackTrace();
              session.write(new DefaultFtpReply(425, "Cannot open data connection."));
              return FtpletResult.SKIP;
            }
          System.out.println("RCVD onDownloadStart 3.5");

            out.transferToClient(session, in);
            session.write(new DefaultFtpReply(226, "Data transfer okay."));
        } catch (Exception ex) {
System.out.println("RCVD onDownloadStart 4");
            session.write(new DefaultFtpReply(551, "Data transfer failed."));
        } finally {
            try { session.getDataConnection().closeDataConnection(); } catch (Exception ignore) {}
            try { in.close(); } catch (Exception ignore) {}
        }
        return FtpletResult.SKIP;
    }

  private InputStream getInputStream(String requestedFile) throws FileNotFoundException {
    //InputStream in = new FileInputStream("./sample-file.txt");
    InputStream in = getClass().getClassLoader().getResourceAsStream("sample-file.txt");
    return in;
  }


}
