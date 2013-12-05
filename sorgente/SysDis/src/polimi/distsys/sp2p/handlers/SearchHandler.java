
package polimi.distsys.sp2p.handlers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.Vector;

import polimi.distsys.sp2p.containers.IncompleteSharedFile;
import polimi.distsys.sp2p.containers.LocalSharedFile;
import polimi.distsys.sp2p.containers.NodeInfo;
import polimi.distsys.sp2p.containers.RemoteSharedFile;
import polimi.distsys.sp2p.containers.SharedFile;

/**
 * @author Ale
 * 
 * Classe contenitore per i metodi utilizzati per effettuare le ricerche
 */
public class SearchHandler {

	/**
	 * implementazione banale guardando se le stringhe che contengono la parola cercata
	 * @param query
	 * @param list
	 * @return
	 */
	public static List<RemoteSharedFile> localSearch(String query, List<RemoteSharedFile> list) {

		List<RemoteSharedFile> result = new Vector<RemoteSharedFile>();
		
		for( RemoteSharedFile f : list ){
			if( matchQuery(f, query) ){
				if( ! result.contains( f ) ){
					result.add( f );
					
				}else{
					RemoteSharedFile mine = result.get( result.indexOf( f ) );
					mine.merge( f );
				}
			}
		}
		
		return result;

	}
	
	public static IncompleteSharedFile searchLocal( SharedFile toSearch, Set<LocalSharedFile> completed, Set<IncompleteSharedFile> incompleted ) throws IOException{
		for( LocalSharedFile localFile : completed ){
			if( toSearch.equals( localFile ) ){
				return new IncompleteSharedFile( localFile );
			}
		}
		
		for( IncompleteSharedFile incomplete : incompleted ){
			if( toSearch.equals( incomplete ) ){
				return incomplete;
			}
		}

		return null;
	}
	
	public static RemoteSharedFile localSearchByHash( byte[] hash, List<RemoteSharedFile> list ) {

		for( RemoteSharedFile f : list ){
			if( Arrays.equals( f.getHash(), hash ) ){
				return f;
			}
		}
		
		return null;

	}
	
	/**
	 * Unisce due liste di file condivisi controllando eventuali duplicati
	 * 
	 * @param list
	 * @param set
	 * @return
	 */
	public static List<RemoteSharedFile> mergeLists(List<RemoteSharedFile> list, List<RemoteSharedFile> set){ 

		//Aggiunge i file alla lista se non ci sono
		for(RemoteSharedFile f : set) {
			if(!list.contains(f))
				list.add(f);
			else {
				// se il file è già presente allora lo aggiunge al numero dei peers
				RemoteSharedFile tmp = list.get(list.indexOf(f));
				tmp.merge(f);
			}
		}
		return list;

	}
	
	/**
	 * 
	 * @param sf
	 * @param query
	 * @return
	 */
	private static boolean matchQuery( SharedFile sf, String query ){
		
		//tokens della query
		List<String> tokens = Arrays.asList( query.split(" ") );
		for( String name : sf.getFileNames() ){
			//tokens del nome del file

			List<String> pieces = new ArrayList<String>( Arrays.asList( name.split(" " ) ) );

			// intersezione tra le due liste
			pieces.retainAll( tokens );
			// se l'intersezione non è vuota, abbiamo un match
			if( pieces.size() > 0 ){
				return true;
			}
		}
		return false;
	}
	
	public static List<RemoteSharedFile> filterOutNode( List<RemoteSharedFile> list, NodeInfo toFilter ){
		for(int i=0;i<list.size();){
			if( list.get( i ).getPeers().contains( toFilter ) ){
				list.get( i ).removePeer( toFilter );
				if( ! list.get( i ).hasPeers() ){
					list.remove( i );
					continue;
				}
			}
			i++;
		}
		return list;
	}
}
