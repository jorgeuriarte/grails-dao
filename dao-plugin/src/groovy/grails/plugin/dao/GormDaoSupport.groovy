package grails.plugin.dao

import org.codehaus.groovy.grails.commons.GrailsClassUtils
import org.springframework.transaction.interceptor.TransactionAspectSupport
import grails.validation.ValidationException
import org.springframework.dao.DataAccessException
import org.springframework.dao.DataIntegrityViolationException

class GormDaoSupport {
	static def log = org.apache.log4j.Logger.getLogger(GormDaoSupport)
	//injected bean
	//def grailsApplication
	
	boolean flushOnSave = false
	
	boolean fireEvents = true

	private Class thisDomainClass
	
	
	
	public GormDaoSupport(){}
	
	public GormDaoSupport(Class clazz){
		thisDomainClass = clazz
	}
	
	public GormDaoSupport(Class clazz,boolean fireEvents){
		thisDomainClass = clazz
		this.fireEvents = fireEvents
	}
	
	/**
	 * returns an instance with fireEvents=false and flushOnSave=false
	 * FIXME this should return a transactional instance
	 */
	static GormDaoSupport getInstance(Class clazz){
/*		def dao = DaoUtils.ctx.getBean("gormDaoBean")
		dao.domainClass = clazz
		return dao*/
		def dao = new GormDaoSupport(clazz,false)
		return dao
	}

	//override this to set the domain this dao is for
	Class getDomainClass(){ return thisDomainClass}
	//set this is constructing a base dao by hand
	void setDomainClass(Class clazz){ thisDomainClass = clazz}

	/**
	* saves a domain entity and rewraps ValidationException with GormDataException on error
	*
	* @param  entity  the domain entity to call save on
	* @throws GormDataException if a validation or DataAccessException error happens
	*/
	def save(entity) {
		save(entity,[flush:flushOnSave])		
	}
	
	/**
	* saves a domain entity with the passed in args and rewraps ValidationException with GormDataException on error
	*
	* @param  entity  the domain entity to call save on
	* @param  args  the arguments to pass to save
	* @throws GormDataException if a validation or DataAccessException error happens
	*/
	def save(entity, Map args) {
		args['failOnError'] = true
		try{
			if(fireEvents) DaoUtils.triggerEvent(this,"beforeSave", entity,null)
			entity.save(args)
		}
		catch (ValidationException ve){
			if(ve instanceof GormDataException) throw ve
			throw new GormDataException(DaoUtils.saveFailedMessage(entity), entity, ve.errors, ve)
		}
		catch (DataAccessException dae) {
			log.error("dao save error on ${entity.id} of ${entity.class.name}",dae)
			//TODO we can build a better message with optimisticLockingFailureMessage(entity) if dae.cause instanceof org.springframework.dao.OptimisticLockingFailureException
			throw new GormDataException(DaoUtils.saveFailedMessage(entity), entity, dae)
		}
		
	}

	/**
	* calls delete always with flush = true so we can intercept any DataIntegrityViolationExceptions 
	*
	* @param  entity  the domain entity
	* @throws GormDataException if a spring DataIntegrityViolationException is thrown
	*/
	def delete(entity){
		try {
			if(fireEvents) DaoUtils.triggerEvent(this,"beforeDelete", entity,null)
			entity.delete(flush:true)
		}
		catch (DataIntegrityViolationException dae) {
			def ident = DaoUtils.badge(entity.id,entity)
			log.error("dao delete error on ${entity.id} of ${entity.class.name}",dae)
			throw new GormDataException(DaoUtils.deleteMessage(entity,ident,false), entity,dae)
		}
	}

	/**
	* inserts and calls save for a new domain entity based with the data from params
	*
	* @param  params  the parameter map
	* @throws GormDataException if a validation error happens
	*/
	Map insert(Map params) {
		formatParams(params)
		def entity = domainClass.newInstance()
		entity.properties = params
		if(fireEvents) DaoUtils.triggerEvent(this,"beforeInsertSave", entity, params)
		save(entity)
		return [ok:true,entity: entity, message:DaoUtils.createMessage(entity)]
	}


	/**
	* updates a new domain entity with the data from params
	*
	* @param  params  the parameter map
	* @throws GormDataException if a validation error happens or its not found with the params.id or the version is off and someone else edited it
	*/
	Map update(Map params){
		def entity = domainClass.get(params.id.toLong())
		formatParams(params)

		DaoUtils.checkFound(entity,params,domainClass.name)
		DaoUtils.checkVersion(entity,params.version)

		entity.properties = params
		if(fireEvents) DaoUtils.triggerEvent(this,"beforeUpdateSave", entity,params)
		save(entity)
		return [ ok:true, entity: entity,message:DaoUtils.updateMessage(entity)]

	}

	/**
	* deletes a new domain entity base on the id in the params
	*
	* @param  params  the parameter map that has the id for the domain entity to delete
	* @throws GormDataException if its not found or if a DataIntegrityViolationException is thrown
	*/
	Map remove(Map params){
		def entity = domainClass.get(params.id.toLong())
		DaoUtils.checkFound(entity,params,domainClass.name)
		if(fireEvents) DaoUtils.triggerEvent(this,"beforeRemoveSave", entity,params)
		def msg = DaoUtils.deleteMessage(entity,DaoUtils.badge(entity.id,entity),true)
		delete(entity)
		return [ok:true, id: params.id,message:msg]
	}
	
	//some our standard naming conventions on fields to clean up
	void formatParams(Map params){
		params.each{
			if(it.key.toLowerCase().endsWith("amount") || it.key.toLowerCase().endsWith("amount2") || it.key.toLowerCase().endsWith("price")
					|| it.key.toLowerCase().startsWith("unitprice")){
				it.value = it.value.toString().replace('$', '').replace(',', '')
			}
			if(it.key.toLowerCase().endsWith("percent") || it.key.toLowerCase().endsWith("pct")){
				 it.value = it.value.toString().replace('%', '')
			}
			if(it.key.toLowerCase().equals("docdate") || it.key.toLowerCase().equals("discdate") || it.key.toLowerCase().equals("duedate") || it.key.toLowerCase().endsWith("duedate")) {
				if(it.value && it.value instanceof String){
					it.value = new Date(it.value)
				}
			}
		}
	}

}
