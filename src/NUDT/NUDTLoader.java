package NUDT;

import adf.component.AbstractLoader;
import adf.component.tactics.TacticsAmbulanceTeam;
import adf.component.tactics.TacticsFireBrigade;
import adf.component.tactics.TacticsPoliceForce;
import adf.component.tactics.TacticsAmbulanceCentre;
import adf.component.tactics.TacticsFireStation;
import adf.component.tactics.TacticsPoliceOffice;
import NUDT.tactics.*;


/**
 * 
 * @author gxd
 */
public class NUDTLoader extends AbstractLoader{

	@Override
	public String getTeamName() {
		return "NUDT";
	}

	@Override
	public TacticsAmbulanceTeam getTacticsAmbulanceTeam() {
		return new NUDTTacticsAmbulanceTeam();
	}

	@Override
	public TacticsFireBrigade getTacticsFireBrigade() {
		return new NUDTTacticsFireBrigade();
	}

	@Override
	public TacticsPoliceForce getTacticsPoliceForce() {
		return new NUDTTacticsPoliceForce();
	}

	@Override
	public TacticsAmbulanceCentre getTacticsAmbulanceCentre() {
		return new NUDTTacticsAmbulanceCentre();
	}

	@Override
	public TacticsFireStation getTacticsFireStation() {
		return new NUDTTacticsFireStation();
	}

	@Override
	public TacticsPoliceOffice getTacticsPoliceOffice() {
		return new NUDTTacticsPoliceOffice();
	}
	
	
}


