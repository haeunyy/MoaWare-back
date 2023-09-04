package com.greedy.moaware.payment.service;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.io.FilenameUtils;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.greedy.moaware.employee.dto.EmpDto;
import com.greedy.moaware.employee.dto.FileCategoryDto;
import com.greedy.moaware.employee.entity.Emp;
import com.greedy.moaware.employee.repository.EmpRepository;
import com.greedy.moaware.payment.dto.FormDto;
import com.greedy.moaware.payment.dto.PayAttachedFileDto;
import com.greedy.moaware.payment.dto.PayEmpDto;
import com.greedy.moaware.payment.dto.PayFileCategoryDto;
import com.greedy.moaware.payment.dto.PaymentDto;
import com.greedy.moaware.payment.dto.PaymentMemberDto;
import com.greedy.moaware.payment.entity.Form;
import com.greedy.moaware.payment.entity.PayAttachedFile;
import com.greedy.moaware.payment.entity.PayEmp;
import com.greedy.moaware.payment.entity.Payment;
import com.greedy.moaware.payment.entity.PaymentMember;
import com.greedy.moaware.payment.entity.PaymentMemberPk;
import com.greedy.moaware.payment.repository.FormRepository;
import com.greedy.moaware.payment.repository.PayAttachedFileRepository;
import com.greedy.moaware.payment.repository.PayEmpRepository;
import com.greedy.moaware.payment.repository.PaymentMemberRepository;
import com.greedy.moaware.payment.repository.PaymentRepository;
import com.greedy.moaware.util.FileUploadUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class PaymentService {
	
	private final PaymentRepository paymentRepository;
	private final FormRepository formRepository;
	private final PayEmpRepository payEmpRepository;
	private final PayAttachedFileRepository payAttachedFileRepository;
	private final PaymentMemberRepository paymentMemberRepository;
	private final EmpRepository empRepository;
	private final ModelMapper modelMapper;
	
	@Value("${image.image-dir}")
	private String IMAGE_DIR;
	
	@Value("${image.image-url}")
	private String IMAGE_URL;
	
	public PaymentService(ModelMapper modelMapper, PaymentRepository paymentRepository, FormRepository formRepository
			, PayEmpRepository payEmpRepository, PayAttachedFileRepository payAttachedFileRepository
			, PaymentMemberRepository paymentMemberRepository, EmpRepository empRepository) {
		this.modelMapper = modelMapper;
		this.paymentRepository = paymentRepository;
		this.formRepository = formRepository;
		this.payEmpRepository = payEmpRepository;
		this.payAttachedFileRepository = payAttachedFileRepository;
		this.paymentMemberRepository = paymentMemberRepository;
		this.empRepository = empRepository;
		
	}
	
	/* 기안자 기안문 전체 조회 */
	public List<PaymentDto> paymentAllList(Integer empCode) {
			
		PayEmp emp = payEmpRepository.findById(empCode).orElseThrow( () -> new IllegalArgumentException("해당 사원이 없습니다. empCode=" + empCode));
		
		List<Payment> payList = paymentRepository.findByEmp(emp);
		
		List<PaymentDto> paysDto = payList.stream().map( pay -> modelMapper.map(pay, PaymentDto.class)).collect(Collectors.toList());
		
		return paysDto;
	}
	

	
	/* 기안문 조회 */
	public Map<String, Object> formSelect(Integer empCode) {
		
		List<Form> formList = formRepository.findAll();
		
		PayEmp emp = payEmpRepository.findById(empCode).orElseThrow( () -> new IllegalArgumentException("해당 사원이 없습니다. empCode=" + empCode));
		
		List<FormDto> formDtoList = formList.stream().map( form-> modelMapper.map(form, FormDto.class)).collect(Collectors.toList());
		
		PayEmpDto empDto = modelMapper.map(emp, PayEmpDto.class);
		
		if(empDto.getPayFileCategory().stream().filter( fileCategory -> fileCategory.getFCategoryType().equals("sign")).count() != 0) {
			List<PayFileCategoryDto> empFileCategoryDto = empDto.getPayFileCategory().stream().filter( fileCategory -> fileCategory.getFCategoryType().equals("sign")).collect(Collectors.toList());
			
			empFileCategoryDto.get(0).getFile().setFilePath(IMAGE_URL + empFileCategoryDto.get(0).getFile().getFilePath());
			
			empDto.setPayFileCategory(empFileCategoryDto);
			}
		
		Map<String, Object> result = new HashMap<>();
		
		result.put("payForm", formDtoList);
		result.put("payEmp", empDto);
		
		
		return result;
		
	}
	
	/* 기안문 저장*/
	@Transactional
	public void insertPayment(Integer empCode, PayAttachedFileDto payAttachedFile) {
		
		PayEmp emp = payEmpRepository.findById(empCode).orElseThrow( () -> new IllegalArgumentException("해당 사원이 없습니다. empCode=" + empCode));
		
		if(payAttachedFile.getFileInfo() != null) {
		
		String fileName = UUID.randomUUID().toString().replace("-","");
		
		String savedName = fileName + "." + FilenameUtils.getExtension(payAttachedFile.getOriginalFileName());
		
		payAttachedFile.setSavedFileName(savedName);
		
		try {
			
			String uploadDir = IMAGE_DIR+"/payment";
			
			String replaceFileName = FileUploadUtils.saveFile(uploadDir, fileName, payAttachedFile.getFileInfo());
			String path = "/payment/" + replaceFileName;

			payAttachedFile.setFilePath(path);
			
		} catch (IOException e) {
			e.printStackTrace();
			
		} 
		
		PayAttachedFile file = modelMapper.map(payAttachedFile, PayAttachedFile.class);
		
		file.getPayFileCategory().getPay().setEmp(emp);
		
		payAttachedFileRepository.save(file);
				
		}
		else {
		Payment pay = modelMapper.map(payAttachedFile.getPayFileCategory().getPay(), Payment.class);
		
		pay.setEmp(emp);
		
		paymentRepository.save(pay);
		
		}
		
	}
	
	
	/* 기안자로 기안문 진행중 조회 */
	public Page<PaymentDto> paymentList(Integer empCode, int page) {
		
		Pageable pageable = PageRequest.of(page -1, 10, Sort.by("payCode").descending());
		
		PayEmp emp = payEmpRepository.findById(empCode).orElseThrow( () -> new IllegalArgumentException("해당 사원이 없습니다. empCode=" + empCode));
		
		String payStatus = "진행중";
		
		Page<Payment> payList = paymentRepository.findByEmpAndPayStatus(pageable, emp, payStatus);
		
		
		Page<PaymentDto> payDtoList = payList.map( pay -> modelMapper.map(pay, PaymentDto.class));
		
		return payDtoList;
	}

	
	/* 결재 대기 조회 */
	public Page<PaymentDto> paymentMemberList(Integer empCode, int page) {
		
		Pageable pageable = PageRequest.of(page -1, 10, Sort.by("payCode").descending());
		
		PaymentMemberPk paymentMemberPk = new PaymentMemberPk();
		
		paymentMemberPk.setEmpCode(empCode);
		
		String PayType = null;
		
		List<PaymentMember> paymentMemberList = paymentMemberRepository.findByPaymentMemberPkEmpCodeAndPayType(empCode, PayType);
		
		String payStatus = "진행중";
		
		Page<Payment> paymentList = paymentRepository.findByPayStatusAndPaymentMemberInOrderByPayCode(pageable, payStatus, paymentMemberList);
		
		Page<PaymentDto> paymentListDto = paymentList.map( pay -> modelMapper.map(pay, PaymentDto.class));

		List<PaymentDto> filterPayList = paymentListDto.getContent();
		
		List<PaymentDto> filterPay = filterPayList.stream().filter( p -> {

			List<PaymentMemberDto> filterPayMember = p.getPaymentMember().stream().filter( f -> {
				
				if(f.getPaymentMemberPk().getEmpCode() == empCode ) {
					Integer rank = f.getPayRank();
					if( f.getPayRank() == 1) {
						return true;
					} else if ( f.getPayRank() > 1 ) {
						
						return p.getPaymentMember().stream().anyMatch( pay -> pay.getPayRank() == rank-1 && "결재".equals(pay.getPayType()));
					}
				}
				return false;
			}).collect(Collectors.toList());
			
			return filterPayMember.size() > 0;
			
		}).collect(Collectors.toList());
		
		Page<PaymentDto> filteredPage = new PageImpl<>(filterPay, paymentListDto.getPageable(), filterPay.size());
		
		return filteredPage;
	}
	
	/* 결재 문서 상세 조회 */
	public PaymentDto paymentDetail ( Integer payCode) {
	
		Payment payment = paymentRepository.findById(payCode).orElseThrow( () -> new IllegalArgumentException("해당 결재문서가 없습니다. payCode=" + payCode) );
		
		PaymentDto paymentDto = modelMapper.map(payment, PaymentDto.class);
		
		PayEmpDto payEmpDto = paymentDto.getEmp();
		
		if(payEmpDto.getPayFileCategory().stream().filter( fileCategory -> fileCategory.getFCategoryType().equals("sign")).count() != 0) {
			List<PayFileCategoryDto> empFileCategoryDto = payEmpDto.getPayFileCategory().stream().filter( fileCategory -> fileCategory.getFCategoryType().equals("sign")).collect(Collectors.toList());
			
			empFileCategoryDto.get(0).getFile().setFilePath(IMAGE_URL + empFileCategoryDto.get(0).getFile().getFilePath());
			
			
			payEmpDto.setPayFileCategory(empFileCategoryDto);
		}
		
		paymentDto.setEmp(payEmpDto);
		
		List<PaymentMemberDto> payMember = paymentDto.getPaymentMember().stream().map(member -> {
			
			List<PayFileCategoryDto> payFileCategory =
			member.getEmp().getPayFileCategory().stream().filter( fileCategory -> {
				if(fileCategory.getFCategoryType().equals("sign"))
				{
					fileCategory.getFile().setFilePath(IMAGE_URL + fileCategory.getFile().getFilePath());
				
					return true;
				}
				
				return false;
			}).collect(Collectors.toList());
			
			member.getEmp().setPayFileCategory(payFileCategory);
			return member;
			}).collect(Collectors.toList());
		paymentDto.setPaymentMember(payMember);
		
		if(paymentDto.getPayFileCategory() != null) {
			paymentDto.getPayFileCategory().getFile().setFilePath(IMAGE_URL + paymentDto.getPayFileCategory().getFile().getFilePath());
		}
		
		
		return paymentDto;
	}
	
	/* 결재 처리 진행 */
	@Transactional
	public void PaymentUpdate (Integer empCode, PaymentDto paymentDto) {
		
		PayEmp emp = payEmpRepository.findById(empCode).orElseThrow( () -> new IllegalArgumentException("해당 사원이 없습니다. empCode=" + empCode));
		
		Payment payment = paymentRepository.findById(paymentDto.getPayCode()).orElseThrow( () -> new IllegalArgumentException("해당 결재 문서가 없습니다. payCode=" + paymentDto.getPayCode()) );
			
		 payment.getPaymentMember().stream().map( member ->{
		
		if(member.getPaymentMemberPk().getEmpCode() == empCode) {
			
			if(paymentDto.getPaymentMember().get(0).getPayType().equals("결재")) {
				
				
				member.setPayType(paymentDto.getPaymentMember().get(0).getPayType());
				member.setPayDate(paymentDto.getPaymentMember().get(0).getPayDate());
				
				if("Y".equals(paymentDto.getPaymentMember().get(0).getPayTotalYn())) {
					member.setPayTotalYn(paymentDto.getPaymentMember().get(0).getPayTotalYn());
					paymentDto.setPayStatus(paymentDto.getPayStatus());
				} else {

					if(member.getPayFinalYn().equals("Y")) {
						paymentDto.setPayStatus("결재완료");
					} else {
						paymentDto.setPayStatus("진행중");
					}
				}
				
				
			} else if(paymentDto.getPaymentMember().get(0).getPayType().equals("반려")) {
				member.setPayType(paymentDto.getPaymentMember().get(0).getPayType());
				member.setPayDate(paymentDto.getPaymentMember().get(0).getPayDate());
				member.setCancleReason(paymentDto.getPaymentMember().get(0).getCancleReason());
				
				paymentDto.setPayStatus("반려");

			}
		} 
		
			
		return member;
		
		}).collect(Collectors.toList());
		
		payment.setPayStatus(paymentDto.getPayStatus());
		
	}
	
	
	/* 결재 완료 조회 */
	public Page<PaymentDto> PaymentCompleteList(Integer empCode, int page) {
		
		Pageable pageable = PageRequest.of(page -1, 10, Sort.by("payCode").descending());
		
		PayEmp emp = payEmpRepository.findById(empCode).orElseThrow( () -> new IllegalArgumentException("해당 사원이 없습니다. empCode=" + empCode));
		
		String payStatus = "결재완료";
		
		Page<Payment> payList = paymentRepository.findByEmpAndPayStatus(pageable, emp, payStatus);
			
		Page<PaymentDto> payDtoList = payList.map( pay -> modelMapper.map(pay, PaymentDto.class));
	
		return payDtoList;
	}
	
	
	/* 결재 반려 조회 */
	public Page<PaymentDto> PaymentRefuseList(Integer empCode, int page) {
		
		Pageable pageable = PageRequest.of(page -1, 10, Sort.by("payCode").descending());
		
		PayEmp emp = payEmpRepository.findById(empCode).orElseThrow( () -> new IllegalArgumentException("해당 사원이 없습니다. empCode=" + empCode));
		
		String payStatus = "반려";
		
		Page<Payment> payList = paymentRepository.findByEmpAndPayStatus(pageable, emp, payStatus);
		
		Page<PaymentDto> payDtoList = payList.map( pay -> modelMapper.map(pay, PaymentDto.class));
		
		return payDtoList;
	}

	/* 임시 저장 조회 */
	public Page<PaymentDto> PaymentStorageList(Integer empCode, int page) {
		
		Pageable pageable = PageRequest.of(page -1, 10, Sort.by("payCode").descending());
		
		PayEmp emp = payEmpRepository.findById(empCode).orElseThrow( () -> new IllegalArgumentException("해당 사원이 없습니다. empCode=" + empCode));
		
		String payStatus = "임시";
		
		Page<Payment> payList = paymentRepository.findByEmpAndPayStatus(pageable, emp, payStatus);
		
		Page<PaymentDto> payDtoList = payList.map( pay -> modelMapper.map(pay, PaymentDto.class));
		
		return payDtoList;
	}

	/* 서명 조회*/
	public EmpDto paymentSign(Integer empCode) {
		
		PayEmp emp = payEmpRepository.findById(empCode).orElseThrow( 
							() -> new IllegalArgumentException("해당 사원이 없습니다. empCode=" + empCode));
		
		Emp empSign = empRepository.findById(empCode).orElseThrow(
							() -> new IllegalArgumentException("해당 사원이 없습니다. empCode=" + empCode));
		
		EmpDto empSignDto = modelMapper.map(empSign, EmpDto.class);
	
		if(empSignDto.getFileCategory().stream().filter( fileCategory -> fileCategory.getFCategoryType().equals("sign")).count() != 0) {
			List<FileCategoryDto> empFileCategoryDto = empSignDto.getFileCategory().stream().filter( fileCategory -> fileCategory.getFCategoryType().equals("sign")).collect(Collectors.toList());
			
			empFileCategoryDto.get(0).getFile().setFilePath(IMAGE_URL + empFileCategoryDto.get(0).getFile().getFilePath());
			
			empSignDto.setFileCategory(empFileCategoryDto);
			}
		
		return empSignDto;
	}
	
	
	/* 서명 저장 */
	@Transactional
	public void paymentSignSaved(Integer empCode, PayAttachedFileDto payAttachedFile) {
		
		PayEmp payEmp = payEmpRepository.findById(empCode).orElseThrow( () -> new IllegalArgumentException("해당 사원이 없습니다. empCode=" + empCode));
		
		
		String fileName = UUID.randomUUID().toString().replace("-","");
	
		String savedName = fileName + "." + FilenameUtils.getExtension(	payAttachedFile.getOriginalFileName());
		
		
		payAttachedFile.setSavedFileName(savedName);
		payAttachedFile.getPayFileCategory().setFCategoryName( payEmp.getEmpName() + " 싸인");
		
		
		try {
			
			String uploadDir = IMAGE_DIR+"/sign";
						
			String replaceFileName = FileUploadUtils.saveFile(uploadDir, fileName, payAttachedFile.getFileInfo());
			String path = "/sign/" + replaceFileName;			
			
			payAttachedFile.setFilePath(path);
			
		} catch (IOException e) {
			e.printStackTrace();
			
		} 
		
		PayAttachedFile file = modelMapper.map(payAttachedFile, PayAttachedFile.class);
		
		file.getPayFileCategory().setPayEmp(payEmp);
		
		
		payAttachedFileRepository.save(file);
				
		}
	
	/* 서명 업데이트 */
	@Transactional
	public void paymentSignUpdate(Integer empCode, PayAttachedFileDto payAttachedFile) {
		
		PayEmp payEmp = payEmpRepository.findById(empCode).orElseThrow( () -> new IllegalArgumentException("해당 사원이 없습니다. empCode=" + empCode));
		
		PayAttachedFile originFile = payAttachedFileRepository.findById(payAttachedFile.getFileCode())
						.orElseThrow( () -> new IllegalArgumentException("해당 서명이 없습니다. FileCode=" + payAttachedFile.getFileCode()));
		
		String fileName = UUID.randomUUID().toString().replace("-","");
		
		String savedName = fileName + "." + FilenameUtils.getExtension(	payAttachedFile.getOriginalFileName());
		
		
		try {
			
			String uploadDir = IMAGE_DIR+"/sign";
			
			String replaceFileName = FileUploadUtils.saveFile(uploadDir, fileName, payAttachedFile.getFileInfo());
			String path = "/sign/" + replaceFileName;

			FileUploadUtils.deleteFile(uploadDir, originFile.getSavedFileName());
		
			originFile.update( payAttachedFile.getOriginalFileName(), path, savedName);
			
		} catch (IOException e) {
			e.printStackTrace();
			
		} 
		
		
	}
	
	/* 임시 저장문서 업데이트 */
	@Transactional
	public void updatePaymentStorage (Integer empCode, PayAttachedFileDto payAttachedFile) {
		
		
		PayEmp payEmp = payEmpRepository.findById(empCode).orElseThrow( () -> new IllegalArgumentException("해당 사원이 없습니다. empCode=" + empCode));	
		
		if(payAttachedFile.getFileInfo() != null) {
			
			Payment payment = paymentRepository.findById(payAttachedFile.getPayFileCategory().getPay().getPayCode())
					.orElseThrow( () -> new IllegalArgumentException("해당 결재문서가 없습니다. PayCode=" + payAttachedFile.getPayFileCategory().getPay().getPayCode()));
			
			if(payment.getPayFileCategory() != null ) {
		
			PayAttachedFile originFile = payment.getPayFileCategory().getFile();

			
			String fileName = UUID.randomUUID().toString().replace("-","");
			
			String savedName = fileName + "." + FilenameUtils.getExtension(payAttachedFile.getOriginalFileName());
			
			originFile.setSavedFileName(savedName);
			
			
			try {
				
				String uploadDir = IMAGE_DIR+"/payment";
				
				
				String replaceFileName = FileUploadUtils.saveFile(uploadDir, fileName, payAttachedFile.getFileInfo());
				String path = "/payment/" + replaceFileName;
				
				
				FileUploadUtils.deleteFile(uploadDir, originFile.getSavedFileName());
				
				
				originFile.update( payAttachedFile.getOriginalFileName(), path, savedName);
				
				
			} catch (IOException e) {
				e.printStackTrace();
				
			} 
			
			
			originFile.getPayFileCategory().getPay().setEmp(payEmp);
			
			
			} else {
				String fileName = UUID.randomUUID().toString().replace("-","");
				
				String savedName = fileName + "." + FilenameUtils.getExtension(payAttachedFile.getOriginalFileName());
				
				payAttachedFile.setSavedFileName(savedName);
				
				try {
					
					String uploadDir = IMAGE_DIR+"/payment";
					
					
					String replaceFileName = FileUploadUtils.saveFile(uploadDir, fileName, payAttachedFile.getFileInfo());
					String path = "/payment/" + replaceFileName;
					
					payAttachedFile.setFilePath(path);
					
					
					
				} catch (IOException e) {
					e.printStackTrace();
					
				} 
				
				PayAttachedFile file = modelMapper.map(payAttachedFile, PayAttachedFile.class);
				
				Payment pay = modelMapper.map(payAttachedFile.getPayFileCategory().getPay(), Payment.class);		
				
				if(pay.getPaymentMember() == null && pay.getRefenceMember() ==null) {
					pay.setPaymentMember(payment.getPaymentMember());
					pay.setRefenceMember(payment.getRefenceMember());
				} else if (pay.getPaymentMember() == null && pay.getRefenceMember() !=null){
					
					pay.setPaymentMember(payment.getPaymentMember());
					
				} else if (pay.getPaymentMember() != null && pay.getRefenceMember() == null) {
					pay.setRefenceMember(payment.getRefenceMember());
				}
				
				payment.updatePayment(pay.getPayCode(), pay.getDraftDate(),pay.getDraftTitle(), pay.getDraftContent(), pay.getPayStatus(), pay.getForm(), pay.getPaymentMember(), pay.getRefenceMember());
				
			
				file.getPayFileCategory().setPay(payment);
							
				payAttachedFileRepository.save(file);
						
				}
			
			Payment pay = modelMapper.map(payAttachedFile.getPayFileCategory().getPay(), Payment.class);
			
			
		}
				
		
		else {
			
			Payment payment = paymentRepository.findById(payAttachedFile.getPayFileCategory().getPay().getPayCode())
					.orElseThrow( () -> new IllegalArgumentException("해당 결재문서가 없습니다. PayCode=" + payAttachedFile.getPayFileCategory().getPay().getPayCode()));
			
			Payment pay = modelMapper.map(payAttachedFile.getPayFileCategory().getPay(), Payment.class);
			
			
			if(payment.getPayFileCategory() != null ) {

				if(payAttachedFile.getSavedFileName() != null) {

					if(pay.getPaymentMember() == null && pay.getRefenceMember() ==null) {
						pay.setPaymentMember(payment.getPaymentMember());
						pay.setRefenceMember(payment.getRefenceMember());
					} else if (pay.getPaymentMember() == null && pay.getRefenceMember() !=null){
						
						pay.setPaymentMember(payment.getPaymentMember());
						
					} else if (pay.getPaymentMember() != null && pay.getRefenceMember() == null) {
						pay.setRefenceMember(payment.getRefenceMember());
					} 
					
					payment.updatePayment(pay.getPayCode(), pay.getDraftDate(),pay.getDraftTitle(), pay.getDraftContent(), pay.getPayStatus(), pay.getForm(), pay.getPayFileCategory(), pay.getPaymentMember(), pay.getRefenceMember());
					
					
				} else {
					
					PayAttachedFile originFile = payment.getPayFileCategory().getFile();
					
					String fileName = UUID.randomUUID().toString().replace("-","");
					
					String savedName = fileName + "." + FilenameUtils.getExtension(payAttachedFile.getOriginalFileName());
										
					
					try {
						
						String uploadDir = IMAGE_DIR+"/payment";
						FileUploadUtils.deleteFile(uploadDir, originFile.getSavedFileName());
						
						payAttachedFileRepository.delete(payment.getPayFileCategory().getFile());

						
					} catch (IOException e) {
						e.printStackTrace();
						
					} 
					
					
					
					if(pay.getPaymentMember() == null && pay.getRefenceMember() ==null) {
						pay.setPaymentMember(payment.getPaymentMember());
						pay.setRefenceMember(payment.getRefenceMember());
					} else if (pay.getPaymentMember() == null && pay.getRefenceMember() !=null){
						
						pay.setPaymentMember(payment.getPaymentMember());
						
					} else if (pay.getPaymentMember() != null && pay.getRefenceMember() == null) {
						pay.setRefenceMember(payment.getRefenceMember());
					} 
					
					payment.updatePayment(pay.getPayCode(), pay.getDraftDate(),pay.getDraftTitle(), pay.getDraftContent(), pay.getPayStatus(), pay.getForm(), pay.getPayFileCategory(), pay.getPaymentMember(), pay.getRefenceMember());
					
				}
				
				
				
			} else {
			
				if(pay.getPaymentMember() == null && pay.getRefenceMember() == null) {
					pay.setPaymentMember(payment.getPaymentMember());
					pay.setRefenceMember(payment.getRefenceMember());
				} else if (pay.getPaymentMember() == null && pay.getRefenceMember() !=null){
					
					pay.setPaymentMember(payment.getPaymentMember());
					
				} else if (pay.getPaymentMember() != null && pay.getRefenceMember() == null) {
					pay.setRefenceMember(payment.getRefenceMember());
				} 
				
				payment.updatePayment(pay.getPayCode(), pay.getDraftDate(),pay.getDraftTitle(), pay.getDraftContent(), pay.getPayStatus(), pay.getForm(), pay.getPayFileCategory(), pay.getPaymentMember(), pay.getRefenceMember());
			}

			
		
		}
		

		
	}
	
	
		
}



