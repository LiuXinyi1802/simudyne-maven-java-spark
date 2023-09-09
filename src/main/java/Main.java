

import simudyne.nexus.Server;
import tokyo.TokyoModel;

public class Main {
  public static void main(String[] args) {
    Server.register("Contagion model in Banking System", TokyoModel.class);

    Server.run(args);//
  }
}
